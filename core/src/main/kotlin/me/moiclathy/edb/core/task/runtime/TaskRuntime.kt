package me.moiclathy.edb.core.task.runtime

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.cancellation.TaskCancellationService
import me.moiclathy.edb.core.task.execution.TaskExecutor
import me.moiclathy.edb.core.task.queue.TaskQueue
import me.moiclathy.edb.core.task.repository.TaskRepository
import java.time.Clock
import java.time.Instant

class TaskRuntime(
    private val queue: TaskQueue,
    private val executor: TaskExecutor,
    private val repository: TaskRepository,
    private val cancellationService: TaskCancellationService,
    private val eventPublisher: TaskLifecycleEventPublisher,
    private val clock: Clock
) : TaskRunner {

    suspend fun submit(task: Task): Task = submitWithOutcome(task).task

    suspend fun submitWithOutcome(task: Task): TaskSubmissionOutcome {
        require(task.id == null) { "A submitted task must not already have an ID." }
        require(task.status == TaskStatus.PENDING) { "A submitted task must be PENDING." }

        task.idempotencyKey?.let { key -> repository.findByIdempotencyKey(key)?.let { return TaskSubmissionOutcome(it, false) } }
        val queued = try {
            queue.enqueue(task, Instant.now(clock))
        } catch (failure: Exception) {
            task.idempotencyKey?.let { key -> repository.findByIdempotencyKey(key)?.let { return TaskSubmissionOutcome(it, false) } }
            throw failure
        }
        eventPublisher.publishCreated(queued)
        eventPublisher.publishTransition(TaskStatus.PENDING, queued)
        return TaskSubmissionOutcome(queued, true)
    }

    override suspend fun runOnce(): Boolean {
        val claimed = queue.claimNext(Instant.now(clock)) ?: return false
        eventPublisher.publishTransition(TaskStatus.QUEUED, claimed)

        val outcome = executor.execute(claimed)
        if (outcome.status != claimed.status) {
            eventPublisher.publishTransition(claimed.status, outcome)
        }
        return true
    }

    suspend fun requestCancellation(taskId: Int, requestedBy: Int): Task {
        val before = repository.findById(taskId)
            ?: throw IllegalArgumentException("Task not found: $taskId")
        val after = cancellationService.requestCancellation(taskId, requestedBy)

        eventPublisher.publishCancellationRequested(after, before.status)
        if (after.status != before.status) {
            eventPublisher.publishTransition(before.status, after)
        }
        return after
    }
}

data class TaskSubmissionOutcome(val task: Task, val created: Boolean)
