package me.moiclathy.edb.core.task.application

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.runtime.TaskLifecycleEventPublisher
import java.time.Clock
import java.time.Instant

class TaskRetryAdminService(
    private val repository: TaskRepository,
    private val eventPublisher: TaskLifecycleEventPublisher,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun requeue(taskId: Int, requestedBy: Int): Task {
        require(taskId > 0) { "Task ID must be greater than zero." }
        require(requestedBy > 0) { "Requesting administrator ID must be greater than zero." }
        val before = repository.findById(taskId) ?: throw TaskNotFoundException(taskId)
        val requeued = repository.requeueFailed(taskId, requestedBy, Instant.now(clock))
        eventPublisher.publishTransition(before.status, requeued)
        return requeued
    }
}
