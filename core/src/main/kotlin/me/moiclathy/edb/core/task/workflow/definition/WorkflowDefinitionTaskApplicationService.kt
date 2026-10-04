package me.moiclathy.edb.core.task.workflow.definition

import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.exception.PermissionDeniedException
import me.moiclathy.edb.core.exception.ProjectNotFoundException
import me.moiclathy.edb.core.security.PermissionChecker
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskPriority
import me.moiclathy.edb.core.task.retry.RetryPolicy
import me.moiclathy.edb.core.task.runtime.TaskRuntime
import me.moiclathy.edb.core.task.runtime.TaskSubmissionOutcome
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class SubmitWorkflowDefinitionTaskCommand(
    val definitionId: Long,
    val projectId: Int,
    val expectedVersion: Int,
    val expectedSha256: String,
    val context: Map<String, String> = emptyMap(),
    val requestedBy: Int,
    val idempotencyKey: String? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val retryPolicy: RetryPolicy = RetryPolicy.NONE,
    val timeout: Duration? = null
)

class WorkflowDefinitionTaskApplicationService(
    private val definitions: ProjectWorkflowDefinitionRepository,
    private val runtime: TaskRuntime,
    private val projects: ProjectRepository,
    private val permissions: PermissionChecker,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun submit(command: SubmitWorkflowDefinitionTaskCommand): Task = submitWithOutcome(command).task

    suspend fun submitWithOutcome(command: SubmitWorkflowDefinitionTaskCommand): TaskSubmissionOutcome {
        require(command.definitionId > 0 && command.projectId > 0 && command.requestedBy > 0)
        val project = projects.findById(command.projectId) ?: throw ProjectNotFoundException(command.projectId)
        if (!permissions.requireWritable(command.requestedBy, project)) throw PermissionDeniedException()
        val definition = definitions.findById(command.definitionId)
            ?: throw WorkflowDefinitionNotFoundException(command.definitionId)
        if (definition.projectId != command.projectId || definition.version != command.expectedVersion ||
            definition.workflowSha256 != command.expectedSha256) throw WorkflowDefinitionMismatchException(command.definitionId)
        return runtime.submitWithOutcome(Task.pending(
            projectId = command.projectId,
            taskType = WorkflowDefinitionTaskHandler.TASK_TYPE,
            configSnapshot = WorkflowDefinitionTaskPayloadCodec.encode(WorkflowDefinitionTaskPayload(
                command.definitionId, command.expectedVersion, command.expectedSha256, command.context
            )),
            triggerUserId = command.requestedBy,
            createdAt = Instant.now(clock),
            idempotencyKey = command.idempotencyKey,
            priority = command.priority,
            retryPolicy = command.retryPolicy,
            timeout = command.timeout
        ))
    }
}

class WorkflowDefinitionNotFoundException(id: Long) : IllegalArgumentException("Workflow definition was not found: $id")
class WorkflowDefinitionMismatchException(id: Long) : IllegalArgumentException(
    "Workflow definition project, version, or SHA-256 does not match: $id"
)
