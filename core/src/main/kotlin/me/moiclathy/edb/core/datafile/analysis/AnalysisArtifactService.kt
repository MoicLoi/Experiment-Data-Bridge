package me.moiclathy.edb.core.datafile.analysis

import me.moiclathy.edb.core.datafile.format.DataFileFormatRegistry
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
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
import java.io.ByteArrayInputStream
import java.time.Clock

data class CreateAnalysisArtifactCommand(
    val projectId: Int,
    val sourceDataFileIds: Set<Int>,
    val requestedBy: Int,
    val producingTaskId: Int,
    val analysisType: String,
    val analysisTool: String,
    val outputFormat: DataFileFormat,
    val outputFileName: String,
    val content: InputStream,
    val mediaType: String? = null,
    val metadata: DataFileMetadata = DataFileMetadata.EMPTY
) {
    init {
        require(projectId > 0 && requestedBy > 0 && producingTaskId > 0) {
            "Project, requester and producing task ids must be positive."
        }
        require(sourceDataFileIds.isNotEmpty() && sourceDataFileIds.all { it > 0 }) {
            "Analysis requires at least one positive source DataFile id."
        }
        require(analysisType.isNotBlank()) { "Analysis type must not be blank." }
        require(analysisTool.isNotBlank()) { "Analysis tool must not be blank." }
        require(outputFormat != DataFileFormat.UNKNOWN) { "Analysis output format must be known." }
        require(outputFileName.isNotBlank()) { "Analysis output file name must not be blank." }
        require(mediaType == null || mediaType.isNotBlank()) { "Analysis media type must not be blank." }
    }
}

class AnalysisSourceNotFoundException(id: Int) : EDBException(
    code = "DATA_FILE_NOT_FOUND",
    message = "Analysis source DataFile was not found: $id"
)

data class CreateProcessingReportCommand(
    val projectId: Int, val sourceDataFileIds: Set<Int>, val outputDataFileId: Int,
    val requestedBy: Int, val producingTaskId: Int, val operation: String, val json: String
)

/**
 * Stores a completed external or internal analysis output as a lineage-aware DataFile.
 * The caller owns [CreateAnalysisArtifactCommand.content] and remains responsible for closing it.
 */
class AnalysisArtifactService(
    private val repository: DataFileRepository,
    private val storage: DataFileStorage,
    private val formatRegistry: DataFileFormatRegistry,
    private val fileNameSanitizer: DataFileNameSanitizer,
    private val storageKeyGenerator: DataFileStorageKeyGenerator,
    private val clock: Clock = Clock.systemUTC()
) {
    fun createProcessingReport(command: CreateProcessingReportCommand): DataFile {
        val parentIds = command.sourceDataFileIds + command.outputDataFileId
        val fileName = "${command.operation}-${command.producingTaskId}.provenance.json"
        val storageKey = storageKeyGenerator.generate(command.projectId, fileName)
        val stored = storage.store(storageKey, ByteArrayInputStream(command.json.toByteArray(Charsets.UTF_8)))
        val now = clock.instant()
        val report = DataFile(
            projectId = command.projectId, createdByUserId = command.requestedBy,
            kind = DataFileKind.AUXILIARY, format = DataFileFormat.JSON, status = DataFileStatus.AVAILABLE,
            originalFileName = fileName, mediaType = "application/vnd.edb.processing-provenance+json",
            sizeBytes = stored.sizeBytes, sha256 = stored.sha256, storage = stored.reference,
            provenance = DataFileProvenance(DataFileOrigin.ANALYSIS, parentIds, command.producingTaskId),
            metadata = DataFileMetadata(mapOf(
                "reportType" to DataFileMetadataValue.StringValue("processing-provenance"),
                "operation" to DataFileMetadataValue.StringValue(command.operation),
                "outputDataFileId" to DataFileMetadataValue.NumberValue(command.outputDataFileId.toBigDecimal())
            )), createdAt = now, updatedAt = now
        )
        return try { repository.save(report) } catch (failure: Exception) {
            storage.compensateDelete(stored.reference.key, failure); throw failure
        }
    }

    fun create(command: CreateAnalysisArtifactCommand): DataFile {
        val sources = command.sourceDataFileIds.map { id ->
            repository.findById(id)
                ?.takeIf { it.projectId == command.projectId }
                ?: throw AnalysisSourceNotFoundException(id)
        }
        require(sources.all { it.status == DataFileStatus.AVAILABLE }) {
            "All analysis source DataFiles must be AVAILABLE."
        }

        val fileName = fileNameSanitizer.sanitize(command.outputFileName)
        val storageKey = storageKeyGenerator.generate(command.projectId, fileName)
        val stored = storage.store(storageKey, command.content)
        val now = clock.instant()
        val systemMetadata = mapOf(
            "analysisType" to DataFileMetadataValue.StringValue(command.analysisType),
            "analysisTool" to DataFileMetadataValue.StringValue(command.analysisTool)
        )
        val dataFile = DataFile(
                projectId = command.projectId,
                createdByUserId = command.requestedBy,
                kind = DataFileKind.ANALYSIS_RESULT,
                format = command.outputFormat,
                status = DataFileStatus.AVAILABLE,
                originalFileName = fileName,
                mediaType = command.mediaType
                    ?: formatRegistry.definition(command.outputFormat)?.defaultMediaType
                    ?: "application/octet-stream",
                sizeBytes = stored.sizeBytes,
                sha256 = stored.sha256,
                storage = stored.reference,
                provenance = DataFileProvenance(
                    origin = DataFileOrigin.ANALYSIS,
                    parentDataFileIds = command.sourceDataFileIds,
                    producingTaskId = command.producingTaskId,
                    externalSystem = command.analysisTool
                ),
                metadata = DataFileMetadata(command.metadata.values + systemMetadata),
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
