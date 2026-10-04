package me.moiclathy.edb.core.datafile.report

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import me.moiclathy.edb.core.task.error.TaskExecutionException
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.execution.TaskHandler
import me.moiclathy.edb.core.task.persistence.TaskPayloadCodec
import me.moiclathy.edb.core.task.result.TaskResult

data class ReportTaskPayload(
    val definitionId: String,
    val sourceDataFileIds: Set<Int> = emptySet(),
    val parameters: Map<String, String> = emptyMap()
) {
    init {
        require(definitionId.isNotBlank()) { "Report task definition id must not be blank." }
        require(sourceDataFileIds.all { it > 0 }) { "Report source DataFile ids must be positive." }
        require(parameters.keys.none { it.isBlank() }) { "Report parameter names must not be blank." }
    }
}

object ReportTaskPayloadCodec {
    private const val DEFINITION_ID = "definitionId"
    private const val SOURCE_IDS = "sourceDataFileIds"
    private const val PARAMETER_PREFIX = "parameter."

    fun encode(payload: ReportTaskPayload): String = TaskPayloadCodec.encode(
        buildMap {
            put(DEFINITION_ID, payload.definitionId)
            put(SOURCE_IDS, payload.sourceDataFileIds.sorted().joinToString(","))
            payload.parameters.forEach { (key, value) -> put("$PARAMETER_PREFIX$key", value) }
        }
    )

    fun decode(value: String?): ReportTaskPayload {
        val values = TaskPayloadCodec.decode(value)
        val definitionId = values[DEFINITION_ID]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Report task payload has no definition id.")
        val sourceIds = parseDataFileIds(values[SOURCE_IDS].orEmpty())
        val parameters = values.filterKeys { it.startsWith(PARAMETER_PREFIX) }
            .mapKeys { it.key.removePrefix(PARAMETER_PREFIX) }
        return ReportTaskPayload(definitionId, sourceIds, parameters)
    }

    internal fun parseDataFileIds(value: String): Set<Int> {
        if (value.isBlank()) return emptySet()
        return value.split(',').mapIndexed { index, raw ->
            raw.trim().toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Invalid source DataFile id at position ${index + 1}: $raw")
        }.toSet()
    }
}

class ReportTaskHandler(
    private val definitions: ReportDefinitionRegistry,
    private val artifactService: ReportArtifactService
) : TaskHandler {
    override val taskType: String = TASK_TYPE

    override suspend fun execute(task: Task, context: TaskExecutionContext): TaskResult {
        val payload = try {
            ReportTaskPayloadCodec.decode(task.configSnapshot)
        } catch (failure: Exception) {
            throw taskFailure(
                "INVALID_REPORT_PAYLOAD",
                TaskErrorCategory.VALIDATION,
                failure.message ?: "Report task payload is invalid.",
                failure
            )
        }
        val definition = try {
            definitions.require(payload.definitionId)
        } catch (failure: ReportDefinitionNotFoundException) {
            throw taskFailure("REPORT_DEFINITION_NOT_FOUND", TaskErrorCategory.CONFIGURATION, failure.message!!, failure)
        }

        try {
            context.ensureNotCancelled()
            context.reportProgress(10, "Preparing report generation")
            val artifact = artifactService.generate(
                GenerateReportArtifactCommand(
                    projectId = task.projectId,
                    definition = definition,
                    sourceDataFileIds = payload.sourceDataFileIds,
                    requestedBy = task.triggerUserId,
                    producingTaskId = requireNotNull(task.id) { "Persisted report task id is required." },
                    parameters = payload.parameters
                )
            )
            context.ensureNotCancelled()
            context.reportProgress(100, "Report generation completed")
            return TaskResult(
                outputPath = artifact.storage?.key,
                contentType = artifact.mediaType,
                metadata = mapOf(
                    "dataFileId" to requireNotNull(artifact.id).toString(),
                    "reportDefinitionId" to definition.id,
                    "format" to artifact.format.name
                )
            )
        } catch (failure: TaskExecutionException) {
            throw failure
        } catch (failure: ReportSourceNotFoundException) {
            throw taskFailure("DATA_FILE_NOT_FOUND", TaskErrorCategory.NOT_FOUND, failure.message!!, failure)
        } catch (failure: ReportGeneratorNotFoundException) {
            throw taskFailure("REPORT_GENERATOR_NOT_FOUND", TaskErrorCategory.CONFIGURATION, failure.message!!, failure)
        } catch (failure: Exception) {
            throw taskFailure(
                "REPORT_GENERATION_FAILED",
                TaskErrorCategory.EXECUTION,
                failure.message ?: "Report generation failed.",
                failure
            )
        }
    }

    companion object {
        const val TASK_TYPE = "report-generation"
    }
}

private fun taskFailure(
    code: String,
    category: TaskErrorCategory,
    message: String,
    cause: Throwable
) = TaskExecutionException(code, category, message, cause = cause)
