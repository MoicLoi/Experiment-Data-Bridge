package me.moiclathy.edb.core.task.persistence

import me.moiclathy.edb.core.task.Task

object TaskMapper {

    fun toEntity(task: Task): TaskEntity = TaskEntity(
        id = task.id,
        projectId = task.projectId,
        taskType = task.taskType,
        status = task.status,
        priority = task.priority,
        retryPolicy = task.retryPolicy,
        attemptCount = task.attemptCount,
        nextAttemptAt = task.nextAttemptAt,
        timeout = task.timeout,
        cancellationRequest = task.cancellationRequest,
        progress = task.progress,
        configSnapshot = task.configSnapshot,
        idempotencyKey = task.idempotencyKey,
        triggerUserId = task.triggerUserId,
        startedAt = task.startedAt,
        completedAt = task.completedAt,
        result = task.result,
        error = task.error,
        createdAt = task.createdAt,
        updatedAt = task.updatedAt
    )

    fun toModel(entity: TaskEntity): Task = Task(
        id = entity.id,
        projectId = entity.projectId,
        taskType = entity.taskType,
        status = entity.status,
        priority = entity.priority,
        retryPolicy = entity.retryPolicy,
        attemptCount = entity.attemptCount,
        nextAttemptAt = entity.nextAttemptAt,
        timeout = entity.timeout,
        cancellationRequest = entity.cancellationRequest,
        progress = entity.progress,
        configSnapshot = entity.configSnapshot,
        idempotencyKey = entity.idempotencyKey,
        triggerUserId = entity.triggerUserId,
        startedAt = entity.startedAt,
        completedAt = entity.completedAt,
        result = entity.result,
        error = entity.error,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}
