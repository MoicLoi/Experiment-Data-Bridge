package me.moiclathy.edb.core.task.application

import me.moiclathy.edb.core.database.model.Project
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.exception.PermissionDeniedException
import me.moiclathy.edb.core.exception.ProjectNotFoundException
import me.moiclathy.edb.core.security.PermissionChecker
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskPriority
import me.moiclathy.edb.core.task.audit.TaskAuditEvent
import me.moiclathy.edb.core.task.audit.TaskAuditService
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.pipeline.PipelineTaskHandler
import me.moiclathy.edb.core.task.pipeline.PipelineTaskPayload
import me.moiclathy.edb.core.task.pipeline.PipelineTaskPayloadCodec
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.retry.RetryPolicy
import me.moiclathy.edb.core.task.runtime.TaskRuntime
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class SubmitPipelineTaskCommand(
    val projectId: Int,
    val pipelineId: String,
    val context: Map<String, String> = emptyMap(),
    val requestedBy: Int,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val retryPolicy: RetryPolicy = RetryPolicy.NONE,
    val timeout: Duration? = null
) {
    init {
        require(projectId > 0) { "Project ID must be greater than zero." }
        require(pipelineId.isNotBlank()) { "Pipeline ID must not be blank." }
        require(requestedBy > 0) { "Requesting user ID must be greater than zero." }
        require(timeout == null || timeout.toMillis() >= 1) {
            "Task timeout must be at least one millisecond."
        }
        require(context.keys.none { it.isBlank() }) {
            "Pipeline context keys must not be blank."
        }
    }
}

/** Shared command/query boundary for future HTTP and EventBus task APIs. */
class TaskApplicationService(
    private val runtime: TaskRuntime,
    private val taskRepository: TaskRepository,
    private val pipelineRegistry: PipelineRegistry,
    private val auditService: TaskAuditService,
    private val projectRepository: ProjectRepository,
    private val permissionChecker: PermissionChecker,
    private val clock: Clock
) {
    suspend fun submitPipelineTask(command: SubmitPipelineTaskCommand): Task {
        val project = requireProject(command.projectId)
        requireWritable(command.requestedBy, project)
        if (pipelineRegistry.find(command.pipelineId) == null) {
            throw PipelineNotFoundException(command.pipelineId)
        }

        val task = Task.pending(
            projectId = command.projectId,
            taskType = PipelineTaskHandler.TASK_TYPE,
            configSnapshot = PipelineTaskPayloadCodec.encode(
                PipelineTaskPayload(command.pipelineId, command.context)
            ),
            triggerUserId = command.requestedBy,
            createdAt = Instant.now(clock),
            priority = command.priority,
            retryPolicy = command.retryPolicy,
            timeout = command.timeout
        )
        return runtime.submit(task)
    }

    fun getTask(taskId: Int, requestedBy: Int): Task {
        require(taskId > 0) { "Task ID must be greater than zero." }
        require(requestedBy > 0) { "Requesting user ID must be greater than zero." }
        val task = taskRepository.findById(taskId) ?: throw TaskNotFoundException(taskId)
        requireReadable(requestedBy, requireProject(task.projectId))
        return task
    }

    fun listTasks(query: TaskListQuery): TaskPage {
        requireReadable(query.requestedBy, requireProject(query.projectId))
        val total = taskRepository.count(query.projectId, query.status)
        val items = taskRepository.findPage(
            projectId = query.projectId,
            status = query.status,
            offset = query.page.toLong() * query.size,
            limit = query.size
        )
        return TaskPage(
            items = items,
            page = query.page,
            size = query.size,
            totalElements = total,
            totalPages = if (total == 0L) 0L else (total + query.size - 1) / query.size
        )
    }

    fun ensureProjectReadable(projectId: Int, requestedBy: Int) {
        require(projectId > 0) { "Project ID must be greater than zero." }
        require(requestedBy > 0) { "Requesting user ID must be greater than zero." }
        requireReadable(requestedBy, requireProject(projectId))
    }

    fun getTaskTimeline(taskId: Int, requestedBy: Int): List<TaskAuditEvent> {
        getTask(taskId, requestedBy)
        return auditService.history(taskId)
    }

    suspend fun requestCancellation(taskId: Int, requestedBy: Int): Task {
        val task = getTask(taskId, requestedBy)
        requireWritable(requestedBy, requireProject(task.projectId))
        return runtime.requestCancellation(taskId, requestedBy)
    }

    private fun requireProject(projectId: Int): Project =
        projectRepository.findById(projectId) ?: throw ProjectNotFoundException(projectId)

    private fun requireReadable(userId: Int, project: Project) {
        if (!permissionChecker.requireReadable(userId, project)) {
            throw PermissionDeniedException()
        }
    }

    private fun requireWritable(userId: Int, project: Project) {
        if (!permissionChecker.requireWritable(userId, project)) {
            throw PermissionDeniedException()
        }
    }
}
