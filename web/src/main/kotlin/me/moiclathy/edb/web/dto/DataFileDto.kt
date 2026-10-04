package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue
import me.moiclathy.edb.core.datafile.conversion.SubmitDataFileConversionCommand
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionCapability
import me.moiclathy.edb.core.task.TaskPriority
import java.time.Duration

@Serializable
data class SubmitDataFileConversionRequest(
    val targetFormat: String,
    val options: Map<String, String> = emptyMap(),
    val priority: Int = TaskPriority.NORMAL.value,
    val retry: TaskRetryPolicyRequest = TaskRetryPolicyRequest(),
    val timeoutMs: Long? = null
) {
    fun toCommand(projectId: Int, sourceDataFileId: Int, requestedBy: Int) =
        SubmitDataFileConversionCommand(
            projectId = projectId,
            sourceDataFileId = sourceDataFileId,
            targetFormat = DataFileFormat.entries.firstOrNull {
                it.name.equals(targetFormat.trim(), ignoreCase = true)
            } ?: throw IllegalArgumentException("Unknown DataFile target format: $targetFormat"),
            requestedBy = requestedBy,
            options = options,
            priority = TaskPriority(priority),
            retryPolicy = retry.toDomain(),
            timeout = timeoutMs?.let(Duration::ofMillis)
        )
}

@Serializable
data class DataFileConversionOptionResponse(
    val name: String,
    val type: String,
    val required: Boolean,
    val defaultValue: String?,
    val description: String?
)

@Serializable
data class DataFileConversionCapabilityResponse(
    val sourceFormat: String,
    val targetFormat: String,
    val options: List<DataFileConversionOptionResponse>
) {
    companion object {
        fun from(capability: DataFileConversionCapability) = DataFileConversionCapabilityResponse(
            sourceFormat = capability.sourceFormat.name,
            targetFormat = capability.targetFormat.name,
            options = capability.options.map {
                DataFileConversionOptionResponse(
                    name = it.name,
                    type = it.type.name,
                    required = it.required,
                    defaultValue = it.defaultValue,
                    description = it.description
                )
            }
        )
    }
}

@Serializable
data class DataFileResponse(
    val id: Int,
    val projectId: Int,
    val createdByUserId: Int,
    val kind: String,
    val format: String,
    val status: String,
    val originalFileName: String,
    val mediaType: String?,
    val sizeBytes: Long?,
    val sha256: String?,
    val storageProvider: String?,
    val origin: String,
    val parentDataFileIds: Set<Int>,
    val producingTaskId: Int?,
    val externalSystem: String?,
    val metadata: JsonObject,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(dataFile: DataFile) = DataFileResponse(
            id = requireNotNull(dataFile.id) { "HTTP DataFile response requires a persisted id." },
            projectId = dataFile.projectId,
            createdByUserId = dataFile.createdByUserId,
            kind = dataFile.kind.name,
            format = dataFile.format.name,
            status = dataFile.status.name,
            originalFileName = dataFile.originalFileName,
            mediaType = dataFile.mediaType,
            sizeBytes = dataFile.sizeBytes,
            sha256 = dataFile.sha256,
            storageProvider = dataFile.storage?.provider,
            origin = dataFile.provenance.origin.name,
            parentDataFileIds = dataFile.provenance.parentDataFileIds,
            producingTaskId = dataFile.provenance.producingTaskId,
            externalSystem = dataFile.provenance.externalSystem,
            metadata = JsonObject(dataFile.metadata.values.mapValues { it.value.toJson() }),
            createdAt = dataFile.createdAt.toString(),
            updatedAt = dataFile.updatedAt.toString()
        )
    }
}

private fun DataFileMetadataValue.toJson(): JsonElement = when (this) {
    is DataFileMetadataValue.StringValue -> JsonPrimitive(value)
    is DataFileMetadataValue.NumberValue -> JsonPrimitive(value)
    is DataFileMetadataValue.BooleanValue -> JsonPrimitive(value)
    is DataFileMetadataValue.ObjectValue -> JsonObject(values.mapValues { it.value.toJson() })
    is DataFileMetadataValue.ArrayValue -> JsonArray(values.map { it.toJson() })
    DataFileMetadataValue.NullValue -> JsonNull
}
