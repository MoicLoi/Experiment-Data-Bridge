package me.moiclathy.edb.core.datafile.analysis

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import me.moiclathy.edb.core.task.error.TaskExecutionException
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.execution.TaskHandler
import me.moiclathy.edb.core.task.persistence.TaskPayloadCodec
import me.moiclathy.edb.core.task.result.TaskResult
import java.io.IOException

data class AnalysisTaskPayload(
    val handlerId: String,
    val sourceDataFileIds: Set<Int>,
    val parameters: Map<String, String> = emptyMap()
) {
    init {
        require(handlerId.isNotBlank()) { "Analysis handler id must not be blank." }
        require(sourceDataFileIds.isNotEmpty() && sourceDataFileIds.all { it > 0 }) {
            "Analysis task requires at least one positive source DataFile id."
        }
        require(parameters.keys.none { it.isBlank() }) { "Analysis parameter names must not be blank." }
    }
}

object AnalysisTaskPayloadCodec {
    private const val HANDLER_ID = "handlerId"
    private const val SOURCE_IDS = "sourceDataFileIds"
    private const val PARAMETER_PREFIX = "parameter."

    fun encode(payload: AnalysisTaskPayload): String = TaskPayloadCodec.encode(
        buildMap {
            put(HANDLER_ID, payload.handlerId)
            put(SOURCE_IDS, payload.sourceDataFileIds.sorted().joinToString(","))
            payload.parameters.forEach { (key, value) -> put("$PARAMETER_PREFIX$key", value) }
        }
    )

    fun decode(value: String?): AnalysisTaskPayload {
        val values = TaskPayloadCodec.decode(value)
        val handlerId = values[HANDLER_ID]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Analysis task payload has no handler id.")
        val sourceIds = parseAnalysisSourceIds(values[SOURCE_IDS].orEmpty())
        require(sourceIds.isNotEmpty()) { "Analysis task payload has no source DataFile ids." }
        val parameters = values.filterKeys { it.startsWith(PARAMETER_PREFIX) }
            .mapKeys { it.key.removePrefix(PARAMETER_PREFIX) }
        return AnalysisTaskPayload(handlerId, sourceIds, parameters)
    }
}

internal fun parseAnalysisSourceIds(value: String): Set<Int> {
    if (value.isBlank()) return emptySet()
    return value.split(',').mapIndexed { index, raw ->
        raw.trim().toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Invalid analysis source DataFile id at position ${index + 1}: $raw")
    }.toSet()
}

class AnalysisTaskHandler(
    private val executionService: AnalysisExecutionService
) : TaskHandler {
    override val taskType: String = TASK_TYPE

    override suspend fun execute(task: Task, context: TaskExecutionContext): TaskResult {
        val payload = try {
            AnalysisTaskPayloadCodec.decode(task.configSnapshot)
        } catch (failure: Exception) {
            throw analysisTaskFailure(
                "INVALID_ANALYSIS_PAYLOAD",
                TaskErrorCategory.VALIDATION,
                failure.message ?: "Analysis task payload is invalid.",
                failure
            )
        }
        try {
            context.ensureNotCancelled()
            context.reportProgress(10, "Preparing analysis")
            val execution = executionService.execute(
                ExecuteAnalysisCommand(
                    projectId = task.projectId,
                    handlerId = payload.handlerId,
                    sourceDataFileIds = payload.sourceDataFileIds,
                    requestedBy = task.triggerUserId,
                    producingTaskId = requireNotNull(task.id) { "Persisted analysis task id is required." },
                    parameters = payload.parameters
                )
            )
            val artifact = execution.artifact
            context.ensureNotCancelled()
            context.reportProgress(100, "Analysis completed")
            return TaskResult(
                outputPath = artifact.storage?.key,
                contentType = artifact.mediaType,
                metadata = mapOf(
                    "dataFileId" to requireNotNull(artifact.id).toString(),
                    "provenanceReportDataFileId" to requireNotNull(execution.provenanceReport.id).toString(),
                    "provenanceOperation" to (payload.parameters["operation"] ?: "analysis"),
                    "inputRows" to metadataNumber(artifact, "inputRows"),
                    "outputRows" to metadataNumber(artifact, "outputRows"),
                    "affectedRows" to metadataAffectedRows(artifact),
                    "analysisHandlerId" to payload.handlerId,
                    "format" to artifact.format.name
                ).filterValues { it.isNotEmpty() }
            )
        } catch (failure: AnalysisHandlerNotFoundException) {
            throw analysisTaskFailure("ANALYSIS_HANDLER_NOT_FOUND", TaskErrorCategory.CONFIGURATION, failure.message!!, failure)
        } catch (failure: AnalysisSourceNotFoundException) {
            throw analysisTaskFailure("DATA_FILE_NOT_FOUND", TaskErrorCategory.NOT_FOUND, failure.message!!, failure)
        } catch (failure: TaskExecutionException) {
            throw failure
        } catch (failure: IOException) {
            throw analysisTaskFailure(
                "ANALYSIS_IO_FAILURE",
                TaskErrorCategory.TRANSIENT,
                failure.message ?: "Analysis worker I/O failed.",
                failure,
                retryable = true
            )
        } catch (failure: Exception) {
            throw analysisTaskFailure(
                "ANALYSIS_FAILED",
                TaskErrorCategory.EXECUTION,
                failure.message ?: "Analysis execution failed.",
                failure
            )
        }
    }

    companion object { const val TASK_TYPE = "analysis" }
}

private fun metadataNumber(file: me.moiclathy.edb.core.datafile.model.DataFile, key: String): String =
    (file.metadata.values[key] as? me.moiclathy.edb.core.datafile.model.DataFileMetadataValue.NumberValue)?.value?.toPlainString().orEmpty()

private fun metadataAffectedRows(file: me.moiclathy.edb.core.datafile.model.DataFile): String {
    val values = file.metadata.values["rules"] as? me.moiclathy.edb.core.datafile.model.DataFileMetadataValue.ArrayValue ?: return ""
    return values.values.sumOf { item ->
        val step = item as? me.moiclathy.edb.core.datafile.model.DataFileMetadataValue.ObjectValue
        (step?.values?.get("affectedRows") as? me.moiclathy.edb.core.datafile.model.DataFileMetadataValue.NumberValue)?.value?.toInt() ?: 0
    }.toString()
}

private fun analysisTaskFailure(
    code: String,
    category: TaskErrorCategory,
    message: String,
    cause: Throwable,
    retryable: Boolean = false
) = TaskExecutionException(code, category, message, retryable = retryable, cause = cause)
