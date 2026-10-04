package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskPriority
import me.moiclathy.edb.core.task.application.SubmitPipelineTaskCommand
import me.moiclathy.edb.core.task.application.TaskPage
import me.moiclathy.edb.core.task.audit.TaskAuditEvent
import me.moiclathy.edb.core.task.retry.RetryPolicy
import java.time.Duration
import me.moiclathy.edb.core.task.diagnostic.TaskFailureDiagnosticRegistry

@Serializable
data class TaskRetryPolicyRequest(
    val maxAttempts: Int = 1,
    val initialDelayMs: Long = 1_000,
    val backoffMultiplier: Double = 2.0,
    val maxDelayMs: Long = 60_000
) {
    fun toDomain() = RetryPolicy(
        maxAttempts = maxAttempts,
        initialDelay = Duration.ofMillis(initialDelayMs),
        backoffMultiplier = backoffMultiplier,
        maxDelay = Duration.ofMillis(maxDelayMs)
    )
}

@Serializable
data class SubmitPipelineTaskRequest(
    val projectId: Int,
    val pipelineId: String,
    val context: Map<String, String> = emptyMap(),
    val priority: Int = TaskPriority.NORMAL.value,
    val retry: TaskRetryPolicyRequest = TaskRetryPolicyRequest(),
    val timeoutMs: Long? = null
) {
    fun toCommand(requestedBy: Int) = SubmitPipelineTaskCommand(
        projectId = projectId,
        pipelineId = pipelineId,
        context = context,
        requestedBy = requestedBy,
        priority = TaskPriority(priority),
        retryPolicy = retry.toDomain(),
        timeout = timeoutMs?.let(Duration::ofMillis)
    )
}

@Serializable
data class TaskProgressResponse(
    val percent: Int,
    val message: String?,
    val updatedAt: String
)

@Serializable
data class TaskResultResponse(
    val outputPath: String?,
    val contentType: String?,
    val metadata: Map<String, String>
)

@Serializable
data class TaskErrorResponse(
    val code: String,
    val category: String,
    val message: String,
    val retryable: Boolean,
    val details: Map<String, String>,
    val occurredAt: String
)

@Serializable
data class TaskFailureDiagnosticResponse(val code: String, val title: String, val message: String, val action: String?, val actionLabel: String?, val automaticActionSafe: Boolean)

@Serializable
data class TaskResponse(
    val id: Int,
    val projectId: Int,
    val taskType: String,
    val status: String,
    val priority: Int,
    val attemptCount: Int,
    val maxAttempts: Int,
    val nextAttemptAt: String?,
    val timeoutMs: Long?,
    val cancellationRequestedAt: String?,
    val cancellationRequestedBy: Int?,
    val progress: TaskProgressResponse?,
    val result: TaskResultResponse?,
    val error: TaskErrorResponse?,
    val diagnostic: TaskFailureDiagnosticResponse?,
    val durationMs: Long?,
    val createdAt: String,
    val updatedAt: String,
    val startedAt: String?,
    val completedAt: String?
) {
    companion object {
        private val diagnostics = TaskFailureDiagnosticRegistry.default()

        fun from(task: Task): TaskResponse = TaskResponse(
            id = requireNotNull(task.id) { "HTTP task response requires a persisted task ID." },
            projectId = task.projectId,
            taskType = task.taskType,
            status = task.status.name,
            priority = task.priority.value,
            attemptCount = task.attemptCount,
            maxAttempts = task.retryPolicy.maxAttempts,
            nextAttemptAt = task.nextAttemptAt?.toString(),
            timeoutMs = task.timeout?.toMillis(),
            cancellationRequestedAt = task.cancellationRequest?.requestedAt?.toString(),
            cancellationRequestedBy = task.cancellationRequest?.requestedBy,
            progress = task.progress?.let {
                TaskProgressResponse(it.percent, it.message, it.updatedAt.toString())
            },
            result = task.result?.let {
                TaskResultResponse(it.outputPath, it.contentType, it.metadata)
            },
            error = task.error?.let {
                TaskErrorResponse(
                    code = it.code,
                    category = it.category.name,
                    message = it.message,
                    retryable = it.retryable,
                    details = it.details,
                    occurredAt = it.occurredAt.toString()
                )
            },
            diagnostic = diagnostics.diagnose(task)?.let {
                TaskFailureDiagnosticResponse(it.code, it.title, it.message, it.action?.name, it.actionLabel, it.automaticActionSafe)
            },
            durationMs = task.startedAt?.let { started ->
                (task.completedAt ?: if (task.status.isTerminal) task.updatedAt else null)?.let { completed ->
                    Duration.between(started, completed).toMillis().coerceAtLeast(0)
                }
            },
            createdAt = task.createdAt.toString(),
            updatedAt = task.updatedAt.toString(),
            startedAt = task.startedAt?.toString(),
            completedAt = task.completedAt?.toString()
        )
    }
}

@Serializable
data class TaskPageResponse(
    val items: List<TaskResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Long
) {
    companion object {
        fun from(page: TaskPage) = TaskPageResponse(
            items = page.items.map(TaskResponse::from),
            page = page.page,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages
        )
    }
}

@Serializable
data class TaskAuditEventResponse(
    val id: Long,
    val type: String,
    val fromStatus: String?,
    val toStatus: String?,
    val actorUserId: Int?,
    val details: Map<String, String>,
    val createdAt: String
) {
    companion object {
        fun from(event: TaskAuditEvent) = TaskAuditEventResponse(
            id = requireNotNull(event.id) { "HTTP audit response requires a persisted event ID." },
            type = event.type.name,
            fromStatus = event.fromStatus?.name,
            toStatus = event.toStatus?.name,
            actorUserId = event.actorUserId,
            details = event.details,
            createdAt = event.createdAt.toString()
        )
    }
}

@Serializable
data class TaskTimelineResponse(
    val taskId: Int,
    val events: List<TaskAuditEventResponse>
)
