package me.moiclathy.edb.core.task.pipeline

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import me.moiclathy.edb.core.task.error.TaskExecutionException
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.execution.TaskHandler
import me.moiclathy.edb.core.task.persistence.TaskPayloadCodec
import me.moiclathy.edb.core.task.result.TaskResult

data class PipelineTaskPayload(
    val pipelineId: String,
    val context: Map<String, String> = emptyMap()
) {
    init {
        require(pipelineId.isNotBlank()) { "Pipeline task payload must contain a pipeline id." }
        require(context.keys.none { it.isBlank() }) {
            "Pipeline task context keys must not be blank."
        }
    }
}

object PipelineTaskPayloadCodec {
    private const val PIPELINE_ID_KEY = "pipelineId"
    private const val CONTEXT_PREFIX = "context."

    fun encode(payload: PipelineTaskPayload): String = TaskPayloadCodec.encode(
        buildMap {
            put(PIPELINE_ID_KEY, payload.pipelineId)
            payload.context.forEach { (key, value) ->
                put("$CONTEXT_PREFIX$key", value)
            }
        }
    )

    fun decode(value: String?): PipelineTaskPayload {
        val values = TaskPayloadCodec.decode(value)
        val pipelineId = requireNotNull(values[PIPELINE_ID_KEY]?.takeIf { it.isNotBlank() }) {
            "Pipeline task payload must contain a pipeline id."
        }
        val context = values
            .filterKeys { it.startsWith(CONTEXT_PREFIX) }
            .mapKeys { (key, _) -> key.removePrefix(CONTEXT_PREFIX) }
        return PipelineTaskPayload(pipelineId, context)
    }
}

/** Adapts a registered pipeline to the existing queue/worker TaskHandler contract. */
class PipelineTaskHandler(
    private val registry: PipelineRegistry,
    private val executor: PipelineExecutor
) : TaskHandler {
    override val taskType: String = TASK_TYPE

    override suspend fun execute(
        task: Task,
        context: TaskExecutionContext
    ): TaskResult {
        val payload = decodePayload(task.configSnapshot)
        val pipeline = registry.find(payload.pipelineId)
            ?: throw TaskExecutionException(
                code = "PIPELINE_NOT_FOUND",
                category = TaskErrorCategory.CONFIGURATION,
                message = "Pipeline is not registered: ${payload.pipelineId}",
                details = mapOf("pipelineId" to payload.pipelineId)
            )

        // Check persistent cancellation before every step, not only around the whole handler.
        val guardedPipeline = pipeline.copy(
            steps = pipeline.steps.map { step ->
                PipelineStep(step.id) { pipelineContext ->
                    context.ensureNotCancelled()
                    step.execute(pipelineContext)
                }
            }
        )
        val result = executor.execute(
            pipeline = guardedPipeline,
            initialContext = PipelineContext(payload.context).withValues(
                buildMap {
                    task.id?.let { put(PipelineSystemContextKeys.TASK_ID, it) }
                    put(PipelineSystemContextKeys.PROJECT_ID, task.projectId)
                    put(PipelineSystemContextKeys.TRIGGER_USER_ID, task.triggerUserId)
                }
            )
        )

        val failure = result.failure
        if (failure != null) {
            throw TaskExecutionException(
                code = failure.code,
                category = TaskErrorCategory.EXECUTION,
                message = failure.message,
                retryable = failure.retryable,
                details = mapOf(
                    "pipelineId" to pipeline.id,
                    "stepId" to failure.stepId
                ),
                cause = failure.cause
            )
        }

        val metadata = buildMap {
            put("pipelineId", pipeline.id)
            put("completedSteps", result.completedStepIds.joinToString(","))
            result.context.asMap().forEach { (key, value) ->
                put("context.$key", value?.toString() ?: "null")
            }
        }
        return TaskResult(metadata = metadata)
    }

    private fun decodePayload(value: String?): PipelineTaskPayload = try {
        PipelineTaskPayloadCodec.decode(value)
    } catch (failure: Exception) {
        throw TaskExecutionException(
            code = "INVALID_PIPELINE_PAYLOAD",
            category = TaskErrorCategory.VALIDATION,
            message = failure.message ?: "Pipeline task payload is invalid.",
            cause = failure
        )
    }

    companion object {
        const val TASK_TYPE = "pipeline"
    }
}
