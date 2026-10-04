package me.moiclathy.edb.core.task.persistence

import me.moiclathy.edb.core.task.TaskPriority
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.retry.RetryPolicy
import me.moiclathy.edb.core.task.cancellation.TaskCancellationRequest
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.result.TaskResult
import me.moiclathy.edb.core.task.error.TaskError
import java.time.Duration
import java.time.Instant

/** Database representation of a task row. */
data class TaskEntity(
    val id: Int?,
    val projectId: Int,
    val taskType: String,
    val status: TaskStatus,
    val priority: TaskPriority,
    val retryPolicy: RetryPolicy,
    val attemptCount: Int,
    val nextAttemptAt: Instant?,
    val timeout: Duration?,
    val cancellationRequest: TaskCancellationRequest?,
    val progress: TaskProgress?,
    val configSnapshot: String?,
    val idempotencyKey: String?,
    val triggerUserId: Int,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val result: TaskResult?,
    val error: TaskError?,
    val createdAt: Instant,
    val updatedAt: Instant
)
