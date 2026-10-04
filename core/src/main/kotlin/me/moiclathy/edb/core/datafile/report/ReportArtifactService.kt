package me.moiclathy.edb.core.datafile.report

import me.moiclathy.edb.core.datafile.format.DataFileFormatRegistry
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.DataFileStorage
import me.moiclathy.edb.core.datafile.storage.compensateDelete
import me.moiclathy.edb.core.datafile.upload.DataFileNameSanitizer
import me.moiclathy.edb.core.datafile.upload.DataFileStorageKeyGenerator
import me.moiclathy.edb.core.exception.EDBException
import java.io.InputStream
import java.time.Clock

data class GenerateReportArtifactCommand(
    val projectId: Int,
    val definition: ReportDefinition,
    val sourceDataFileIds: Set<Int> = emptySet(),
    val requestedBy: Int,
    val producingTaskId: Int? = null,
    val parameters: Map<String, String> = emptyMap()
) {
    init {
        require(projectId > 0 && requestedBy > 0) { "Project and requester ids must be positive." }
        require(sourceDataFileIds.all { it > 0 }) { "Report source DataFile ids must be positive." }
        require(producingTaskId == null || producingTaskId > 0) { "Producing task id must be positive." }
        require(parameters.keys.none { it.isBlank() }) { "Report parameter names must not be blank." }
    }
}

class ReportSourceNotFoundException(id: Int) :
    EDBException(
        code = "DATA_FILE_NOT_FOUND",
        message = "Report source DataFile was not found: $id"
    )

class ReportArtifactService(
    private val registry: ReportGeneratorRegistry,
    private val repository: DataFileRepository,
    private val storage: DataFileStorage,
    private val formatRegistry: DataFileFormatRegistry,
    private val fileNameSanitizer: DataFileNameSanitizer,
    private val storageKeyGenerator: DataFileStorageKeyGenerator,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun generate(command: GenerateReportArtifactCommand): DataFile {
        val sources = command.sourceDataFileIds.sorted().map { id ->
            repository.findById(id)
                ?.takeIf { it.projectId == command.projectId }
                ?: throw ReportSourceNotFoundException(id)
        }
        require(sources.all { it.status == DataFileStatus.AVAILABLE }) {
            "All report source DataFiles must be AVAILABLE."
        }
        val generator = registry.require(command.definition)
        val opened = mutableListOf<InputStream>()
        try {
            val inputs = sources.map { source ->
                val storageKey = requireNotNull(source.storage?.key) {
                    "Report source DataFile ${source.id} has no storage reference."
                }
                ReportDataFileInput(source, storage.open(storageKey).also(opened::add))
            }
            val parameters = command.definition.defaultParameters + command.parameters
            return generator.generate(
                ReportGenerationInput(command.definition, inputs, parameters)
            ).use { output ->
                persistArtifact(command, output)
            }
        } finally {
            opened.asReversed().forEach { runCatching { it.close() } }
        }
    }

    private fun persistArtifact(
        command: GenerateReportArtifactCommand,
        output: ReportGeneratorOutput
    ): DataFile {
        val fileName = fileNameSanitizer.sanitize(output.fileName ?: command.definition.outputFileName)
        val key = storageKeyGenerator.generate(command.projectId, fileName)
        val stored = storage.store(key, output.content)
        val now = clock.instant()
        val definitionMetadata = mapOf(
            "reportDefinitionId" to DataFileMetadataValue.StringValue(command.definition.id),
            "reportDefinitionVersion" to DataFileMetadataValue.NumberValue(
                command.definition.version.toBigDecimal()
            ),
            "reportGeneratorId" to DataFileMetadataValue.StringValue(command.definition.generatorId)
        )
        val dataFile = DataFile(
                projectId = command.projectId,
                createdByUserId = command.requestedBy,
                kind = DataFileKind.REPORT,
                format = command.definition.outputFormat,
                status = DataFileStatus.AVAILABLE,
                originalFileName = fileName,
                mediaType = output.mediaType ?: formatRegistry
                    .definition(command.definition.outputFormat)?.defaultMediaType
                    ?: "application/octet-stream",
                sizeBytes = stored.sizeBytes,
                sha256 = stored.sha256,
                storage = stored.reference,
                provenance = DataFileProvenance(
                    origin = DataFileOrigin.REPORT_GENERATION,
                    parentDataFileIds = command.sourceDataFileIds,
                    producingTaskId = command.producingTaskId
                ),
                metadata = DataFileMetadata(output.metadata.values + definitionMetadata),
                createdAt = now,
                updatedAt = now
            )
        return try {
            repository.save(dataFile)
        } catch (failure: Exception) {
            storage.compensateDelete(stored.reference.key, failure)
            throw failure
        }
    }
}
