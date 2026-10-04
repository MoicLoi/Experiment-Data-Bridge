package me.moiclathy.edb.core.datafile.conversion

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
import java.time.Clock
import java.io.ByteArrayInputStream
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.provenance.ProcessingHandlerIdentity
import me.moiclathy.edb.core.datafile.provenance.ProcessingProvenance
import me.moiclathy.edb.core.datafile.provenance.ProcessingProvenanceCodec

data class DataFileConversionResult(val artifact: DataFile, val provenanceReport: DataFile)

data class ExecuteDataFileConversion(
    val projectId: Int,
    val sourceDataFileId: Int,
    val targetFormat: DataFileFormat,
    val requestedBy: Int,
    val producingTaskId: Int,
    val options: Map<String, String> = emptyMap()
)

class DataFileConversionSourceNotFoundException(id: Int) :
    EDBException(
        code = "DATA_FILE_NOT_FOUND",
        message = "Source DataFile was not found: $id"
    )

class DataFileConversionService(
    private val registry: DataFileConversionRegistry,
    private val repository: DataFileRepository,
    private val storage: DataFileStorage,
    private val fileNameSanitizer: DataFileNameSanitizer,
    private val storageKeyGenerator: DataFileStorageKeyGenerator,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun convert(command: ExecuteDataFileConversion): DataFileConversionResult {
        val startedAt = clock.instant()
        val source = repository.findById(command.sourceDataFileId)
            ?: throw DataFileConversionSourceNotFoundException(command.sourceDataFileId)
        require(source.projectId == command.projectId) {
            "Source DataFile does not belong to project ${command.projectId}."
        }
        require(source.status == DataFileStatus.AVAILABLE) {
            "Source DataFile must be AVAILABLE for conversion."
        }
        val sourceKey = requireNotNull(source.storage?.key) {
            "Source DataFile has no storage reference."
        }
        val converter = registry.require(source.format, command.targetFormat)
        return storage.open(sourceKey).use { sourceContent ->
            converter.convert(DataFileConverterInput(source, sourceContent, command.options)).use { converted ->
                val fileName = fileNameSanitizer.sanitize(converted.fileName)
                val key = storageKeyGenerator.generate(command.projectId, fileName)
                val stored = storage.store(key, converted.content)
                val now = clock.instant()
                val metadata = DataFileMetadata(source.metadata.values + converted.metadata.values)
                val dataFile = DataFile(
                        projectId = command.projectId,
                        createdByUserId = command.requestedBy,
                        kind = DataFileKind.DERIVED_DATA,
                        format = command.targetFormat,
                        status = DataFileStatus.AVAILABLE,
                        originalFileName = fileName,
                        mediaType = converted.mediaType ?: command.targetFormat.defaultMediaType(),
                        sizeBytes = stored.sizeBytes,
                        sha256 = stored.sha256,
                        storage = stored.reference,
                        provenance = DataFileProvenance(
                            origin = DataFileOrigin.CONVERSION,
                            parentDataFileIds = setOf(requireNotNull(source.id)),
                            producingTaskId = command.producingTaskId
                        ),
                        metadata = metadata,
                        createdAt = now,
                        updatedAt = now
                    )
                val artifact = try {
                    repository.save(dataFile)
                } catch (failure: Exception) {
                    storage.compensateDelete(stored.reference.key, failure)
                    throw failure
                }
                val completedAt = clock.instant()
                val provenance = ProcessingProvenance(
                    operation = "format-conversion",
                    parameterSnapshot = buildJsonObject {
                        put("sourceFormat", source.format.name); put("targetFormat", command.targetFormat.name)
                        put("options", buildJsonObject { command.options.toSortedMap().forEach { (key, value) -> put(key, JsonPrimitive(value)) } })
                    },
                    steps = listOf(buildJsonObject { put("type", "convertFormat"); put("from", source.format.name); put("to", command.targetFormat.name) }),
                    handler = ProcessingHandlerIdentity(converter.javaClass.name, "kotlin", "1"),
                    sourceDataFileIds = listOf(requireNotNull(source.id)), outputDataFileId = requireNotNull(artifact.id),
                    taskId = command.producingTaskId, userId = command.requestedBy,
                    startedAt = startedAt.toString(), completedAt = completedAt.toString()
                )
                val reportName = "format-conversion-${command.producingTaskId}.provenance.json"
                val reportKey = storageKeyGenerator.generate(command.projectId, reportName)
                val reportStored = storage.store(reportKey, ByteArrayInputStream(ProcessingProvenanceCodec().encode(provenance).toByteArray(Charsets.UTF_8)))
                val report = DataFile(
                    projectId = command.projectId, createdByUserId = command.requestedBy, kind = DataFileKind.AUXILIARY,
                    format = DataFileFormat.JSON, status = DataFileStatus.AVAILABLE, originalFileName = reportName,
                    mediaType = "application/vnd.edb.processing-provenance+json", sizeBytes = reportStored.sizeBytes,
                    sha256 = reportStored.sha256, storage = reportStored.reference,
                    provenance = DataFileProvenance(DataFileOrigin.CONVERSION, setOf(requireNotNull(source.id), requireNotNull(artifact.id)), command.producingTaskId),
                    metadata = DataFileMetadata(mapOf("reportType" to DataFileMetadataValue.StringValue("processing-provenance"), "operation" to DataFileMetadataValue.StringValue("format-conversion"))),
                    createdAt = completedAt, updatedAt = completedAt
                )
                val persistedReport = try { repository.save(report) } catch (failure: Exception) {
                    storage.compensateDelete(reportStored.reference.key, failure); throw failure
                }
                DataFileConversionResult(artifact, persistedReport)
            }
        }
    }
}

private fun DataFileFormat.defaultMediaType(): String = when (this) {
    DataFileFormat.CSV -> "text/csv"
    DataFileFormat.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    DataFileFormat.JSON -> "application/json"
    DataFileFormat.HDF5 -> "application/x-hdf5"
    DataFileFormat.MAT -> "application/x-matlab-data"
    else -> "application/octet-stream"
}
