package me.moiclathy.edb.core.task

import me.moiclathy.edb.core.task.retry.RetryPolicy
import me.moiclathy.edb.core.task.cancellation.TaskCancellationRequest
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.result.TaskResult
import me.moiclathy.edb.core.task.error.TaskError
import java.time.Duration
import java.time.Instant

/**
 * Immutable domain representation of a persisted task.
 *
 * Queue, executor, result, and error details will be introduced by their own
 * 0.25 stages. This model deliberately contains only the stable task identity,
 * lifecycle, ownership, configuration snapshot, and persistence timestamps.
 */
data class Task(
    val id: Int? = null,
    val projectId: Int,
    val taskType: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val retryPolicy: RetryPolicy = RetryPolicy.NONE,
    val attemptCount: Int = 0,
    val nextAttemptAt: Instant? = null,
    val timeout: Duration? = null,
    val cancellationRequest: TaskCancellationRequest? = null,
    val progress: TaskProgress? = null,
    val configSnapshot: String? = null,
    val idempotencyKey: String? = null,
    val triggerUserId: Int,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val result: TaskResult? = null,
    val error: TaskError? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(id == null || id > 0) { "Task ID must be greater than zero." }
        require(projectId > 0) { "Project ID must be greater than zero." }
        require(triggerUserId > 0) { "Trigger user ID must be greater than zero." }
        require(taskType.isNotBlank()) { "Task type must not be blank." }
        require(taskType.length <= MAX_TASK_TYPE_LENGTH) {
            "Task type must not exceed $MAX_TASK_TYPE_LENGTH characters."
        }
        require(idempotencyKey == null || (idempotencyKey.isNotBlank() && idempotencyKey.length <= 191)) {
            "Task idempotency key must be non-blank and at most 191 characters."
        }
        require(attemptCount in 0..retryPolicy.maxAttempts) {
            "Task attempt count must be between zero and maxAttempts."
        }
        require(timeout == null || timeout.toMillis() >= 1) {
            "Task timeout must be at least one millisecond."
        }
        require(!updatedAt.isBefore(createdAt)) {
            "Task updatedAt must not be before createdAt."
        }
        require(cancellationRequest == null || !cancellationRequest.requestedAt.isBefore(createdAt)) {
            "Cancellation request time must not be before task creation."
        }
        require(progress == null || !progress.updatedAt.isBefore(createdAt)) {
            "Task progress time must not be before task creation."
        }
        require(result == null || error == null) {
            "Task result and task error cannot both be present."
        }
    }

    fun transitionTo(
        nextStatus: TaskStatus,
        at: Instant
    ): Task {
        require(status.canTransitionTo(nextStatus)) {
            "Task cannot transition from $status to $nextStatus."
        }
        require(!at.isBefore(updatedAt)) {
            "Task transition time must not be before updatedAt."
        }

        return copy(
            status = nextStatus,
            startedAt = if (nextStatus == TaskStatus.RUNNING && startedAt == null) {
                at
            } else {
                startedAt
            },
            completedAt = if (nextStatus.isTerminal) at else null,
            updatedAt = at
        )
    }

    companion object {
        const val MAX_TASK_TYPE_LENGTH = 50

        fun pending(
            projectId: Int,
            taskType: String,
            configSnapshot: String?,
            triggerUserId: Int,
            createdAt: Instant,
            idempotencyKey: String? = null,
            priority: TaskPriority = TaskPriority.NORMAL,
            retryPolicy: RetryPolicy = RetryPolicy.NONE,
            timeout: Duration? = null
        ): Task = Task(
            projectId = projectId,
            taskType = taskType,
            priority = priority,
            retryPolicy = retryPolicy,
            timeout = timeout,
            configSnapshot = configSnapshot,
            idempotencyKey = idempotencyKey,
            triggerUserId = triggerUserId,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }
}
