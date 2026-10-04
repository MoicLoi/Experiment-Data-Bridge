package me.moiclathy.edb.core.datafile.conversion

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import me.moiclathy.edb.core.task.error.TaskExecutionException
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.execution.TaskHandler
import me.moiclathy.edb.core.task.persistence.TaskPayloadCodec
import me.moiclathy.edb.core.task.result.TaskResult

data class DataFileConversionTaskPayload(
    val sourceDataFileId: Int,
    val targetFormat: DataFileFormat,
    val options: Map<String, String> = emptyMap()
) {
    init {
        require(sourceDataFileId > 0) { "Source DataFile id must be positive." }
        require(targetFormat in DataFileConversionRegistry.SUPPORTED_SCIENTIFIC_FORMATS) {
            "Unsupported DataFile conversion target: $targetFormat"
        }
        require(options.keys.none { it.isBlank() }) { "Conversion option keys must not be blank." }
    }
}

object DataFileConversionTaskPayloadCodec {
    private const val SOURCE_ID = "sourceDataFileId"
    private const val TARGET_FORMAT = "targetFormat"
    private const val OPTION_PREFIX = "option."

    fun encode(payload: DataFileConversionTaskPayload): String = TaskPayloadCodec.encode(
        buildMap {
            put(SOURCE_ID, payload.sourceDataFileId.toString())
            put(TARGET_FORMAT, payload.targetFormat.name)
            payload.options.forEach { (key, value) -> put("$OPTION_PREFIX$key", value) }
        }
    )

    fun decode(value: String?): DataFileConversionTaskPayload {
        val values = TaskPayloadCodec.decode(value)
        val sourceId = values[SOURCE_ID]?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Conversion payload has no valid source DataFile id.")
        val target = values[TARGET_FORMAT]?.let { name ->
            DataFileFormat.entries.firstOrNull { it.name == name }
        } ?: throw IllegalArgumentException("Conversion payload has no valid target format.")
        val options = values.filterKeys { it.startsWith(OPTION_PREFIX) }
            .mapKeys { it.key.removePrefix(OPTION_PREFIX) }
        require(options.keys.none { it.isBlank() }) { "Conversion option keys must not be blank." }
        return DataFileConversionTaskPayload(sourceId, target, options)
    }
}

class DataFileConversionTaskHandler(
    private val conversionService: DataFileConversionService
) : TaskHandler {
    override val taskType: String = TASK_TYPE

    override suspend fun execute(task: Task, context: TaskExecutionContext): TaskResult {
        val payload = try {
            DataFileConversionTaskPayloadCodec.decode(task.configSnapshot)
        } catch (failure: Exception) {
            throw TaskExecutionException(
                code = "INVALID_DATA_FILE_CONVERSION_PAYLOAD",
                category = TaskErrorCategory.VALIDATION,
                message = failure.message ?: "DataFile conversion payload is invalid.",
                cause = failure
            )
        }

        try {
            context.ensureNotCancelled()
            context.reportProgress(10, "Preparing DataFile conversion")
            val execution = conversionService.convert(
                ExecuteDataFileConversion(
                    projectId = task.projectId,
                    sourceDataFileId = payload.sourceDataFileId,
                    targetFormat = payload.targetFormat,
                    requestedBy = task.triggerUserId,
                    producingTaskId = requireNotNull(task.id),
                    options = payload.options
                )
            )
            val converted = execution.artifact
            context.ensureNotCancelled()
            context.reportProgress(100, "DataFile conversion completed")
            return TaskResult(
                outputPath = converted.storage?.key,
                contentType = converted.mediaType,
                metadata = mapOf(
                    "dataFileId" to requireNotNull(converted.id).toString(),
                    "provenanceReportDataFileId" to requireNotNull(execution.provenanceReport.id).toString(),
                    "provenanceOperation" to "format-conversion",
                    "sourceDataFileId" to payload.sourceDataFileId.toString(),
                    "format" to converted.format.name
                )
            )
        } catch (failure: TaskExecutionException) {
            throw failure
        } catch (failure: DataFileConverterNotFoundException) {
            throw TaskExecutionException(
                code = "DATA_FILE_CONVERTER_NOT_FOUND",
                category = TaskErrorCategory.CONFIGURATION,
                message = failure.message ?: "DataFile converter is not registered.",
                cause = failure
            )
        } catch (failure: DataFileConversionSourceNotFoundException) {
            throw TaskExecutionException(
                code = "DATA_FILE_NOT_FOUND",
                category = TaskErrorCategory.NOT_FOUND,
                message = failure.message ?: "Source DataFile was not found.",
                cause = failure
            )
        } catch (failure: Exception) {
            throw TaskExecutionException(
                code = "DATA_FILE_CONVERSION_FAILED",
                category = TaskErrorCategory.EXECUTION,
                message = failure.message ?: "DataFile conversion failed.",
                cause = failure
            )
        }
    }

    companion object {
        const val TASK_TYPE = "data-file-conversion"
    }
}
