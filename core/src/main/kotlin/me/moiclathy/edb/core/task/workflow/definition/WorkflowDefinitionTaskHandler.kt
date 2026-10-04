package me.moiclathy.edb.core.task.workflow.definition

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import me.moiclathy.edb.core.task.error.TaskExecutionException
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.execution.TaskHandler
import me.moiclathy.edb.core.task.persistence.TaskPayloadCodec
import me.moiclathy.edb.core.task.pipeline.*
import me.moiclathy.edb.core.task.result.TaskResult
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCompiler

data class WorkflowDefinitionTaskPayload(
    val definitionId: Long,
    val version: Int,
    val workflowSha256: String,
    val context: Map<String, String> = emptyMap()
)

object WorkflowDefinitionTaskPayloadCodec {
    private const val CONTEXT_PREFIX = "context."
    fun encode(payload: WorkflowDefinitionTaskPayload): String = TaskPayloadCodec.encode(buildMap {
        put("definitionId", payload.definitionId.toString()); put("version", payload.version.toString())
        put("workflowSha256", payload.workflowSha256)
        payload.context.forEach { (key, value) -> put("$CONTEXT_PREFIX$key", value) }
    })

    fun decode(value: String?): WorkflowDefinitionTaskPayload {
        val fields = TaskPayloadCodec.decode(value)
        return WorkflowDefinitionTaskPayload(
            definitionId = fields["definitionId"]?.toLongOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Workflow definition id is missing or invalid."),
            version = fields["version"]?.toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Workflow definition version is missing or invalid."),
            workflowSha256 = fields["workflowSha256"]?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                ?: throw IllegalArgumentException("Workflow definition SHA-256 is missing or invalid."),
            context = fields.filterKeys { it.startsWith(CONTEXT_PREFIX) }.mapKeys { it.key.removePrefix(CONTEXT_PREFIX) }
        )
    }
}

class WorkflowDefinitionTaskHandler(
    private val definitions: ProjectWorkflowDefinitionRepository,
    private val codec: WorkflowIrCodec,
    private val compiler: WorkflowIrCompiler,
    private val executor: PipelineExecutor
) : TaskHandler {
    override val taskType: String = TASK_TYPE

    override suspend fun execute(task: Task, context: TaskExecutionContext): TaskResult {
        val payload = try { WorkflowDefinitionTaskPayloadCodec.decode(task.configSnapshot) } catch (failure: Exception) {
            throw TaskExecutionException("INVALID_WORKFLOW_DEFINITION_PAYLOAD", TaskErrorCategory.VALIDATION,
                failure.message ?: "Workflow definition task payload is invalid.", cause = failure)
        }
        val definition = definitions.findById(payload.definitionId) ?: throw TaskExecutionException(
            "WORKFLOW_DEFINITION_NOT_FOUND", TaskErrorCategory.CONFIGURATION,
            "Workflow definition was not found: ${payload.definitionId}")
        if (definition.projectId != task.projectId || definition.version != payload.version ||
            definition.workflowSha256 != payload.workflowSha256) {
            throw TaskExecutionException("WORKFLOW_DEFINITION_MISMATCH", TaskErrorCategory.CONFIGURATION,
                "Workflow definition identity, project, version, or SHA-256 does not match the task snapshot.")
        }
        val pipeline = compiler.compile(codec.deserialize(definition.workflowIrJson))
        val guarded = pipeline.copy(steps = pipeline.steps.map { step -> PipelineStep(step.id) { state ->
            context.ensureNotCancelled(); step.execute(state)
        } })
        val result = executor.execute(guarded, PipelineContext(payload.context).withValues(buildMap {
            task.id?.let { put(PipelineSystemContextKeys.TASK_ID, it) }
            put(PipelineSystemContextKeys.PROJECT_ID, task.projectId)
            put(PipelineSystemContextKeys.TRIGGER_USER_ID, task.triggerUserId)
        }))
        result.failure?.let { failure -> throw TaskExecutionException(
            failure.code, TaskErrorCategory.EXECUTION, failure.message, failure.retryable,
            mapOf("definitionId" to definition.id.toString(), "workflowId" to definition.workflowId, "stepId" to failure.stepId),
            failure.cause
        ) }
        return TaskResult(metadata = buildMap {
            put("definitionId", definition.id.toString()); put("definitionVersion", definition.version.toString())
            put("workflowId", definition.workflowId); put("workflowSha256", definition.workflowSha256)
            put("completedSteps", result.completedStepIds.joinToString(","))
            result.context.asMap().forEach { (key, value) -> put("context.$key", value?.toString() ?: "null") }
        })
    }

    companion object { const val TASK_TYPE = "workflow-definition" }
}
