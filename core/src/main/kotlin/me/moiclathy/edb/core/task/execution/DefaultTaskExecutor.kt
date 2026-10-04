package me.moiclathy.edb.core.task.execution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.cancellation.TaskCancellationRequestedException
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.error.DefaultTaskErrorClassifier
import me.moiclathy.edb.core.task.error.TaskError
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import me.moiclathy.edb.core.task.error.TaskErrorClassifier
import java.time.Clock
import java.time.Instant
import me.moiclathy.edb.core.task.runtime.TaskLifecycleEventPublisher
import me.moiclathy.edb.core.task.checkpoint.TaskCheckpointRepository

class DefaultTaskExecutor(
    private val registry: TaskHandlerRegistry,
    private val repository: TaskRepository,
    private val clock: Clock,
    private val errorClassifier: TaskErrorClassifier = DefaultTaskErrorClassifier(),
    private val eventPublisher: TaskLifecycleEventPublisher? = null,
    private val checkpointRepository: TaskCheckpointRepository? = null
) : TaskExecutor {

    override suspend fun execute(task: Task): Task {
        require(task.status == TaskStatus.RUNNING) {
            "Only a RUNNING task can be executed."
        }
        require(task.attemptCount >= 1) {
            "A RUNNING task must have at least one recorded attempt."
        }

        val taskId = requireNotNull(task.id) { "A RUNNING task must have an ID." }
        return try {
            val handler = registry.find(task.taskType)
                ?: return finishFailure(
                    task = loadCurrent(task),
                    taskError = TaskError(
                        code = "HANDLER_NOT_FOUND",
                        category = TaskErrorCategory.CONFIGURATION,
                        message = "No task handler registered for type: ${task.taskType}",
                        retryable = false,
                        occurredAt = Instant.now(clock)
                    ),
                    terminalStatus = TaskStatus.FAILED
                )

            val checkpointHandler = handler as? CheckpointTaskHandler
            val checkpointEnabled = checkpointHandler?.supportsCheckpoint(task) == true
            val handlerVersion = checkpointHandler?.handlerVersion?.takeIf { checkpointEnabled }
            require(handlerVersion == null || handlerVersion.isNotBlank()) {
                "Checkpoint handler version must not be blank."
            }
            val storedCheckpoint = checkpointRepository?.findByTaskId(taskId)
            val checkpoint = storedCheckpoint?.takeIf { checkpointEnabled && it.handlerVersion == handlerVersion }
            if (storedCheckpoint != null && checkpoint == null) {
                checkpointRepository?.deleteByTaskId(taskId)
            }
            val context = PersistentTaskExecutionContext(
                taskId = taskId,
                repository = repository,
                clock = clock,
                eventPublisher = eventPublisher,
                checkpointRepository = checkpointRepository?.takeIf { checkpointEnabled },
                checkpointHandlerVersion = handlerVersion,
                initialCheckpoint = checkpoint
            )
            context.ensureNotCancelled()

            val timeout = task.timeout
            val result = if (timeout == null) {
                handler.execute(task, context)
            } else {
                withTimeout(timeout.toMillis()) {
                    handler.execute(task, context)
                }
            }

            context.ensureNotCancelled()
            val current = loadCurrent(task)
            val completedAt = transitionTime(current)
            val completed = current
                .transitionTo(TaskStatus.COMPLETED, completedAt)
                .copy(
                    result = result,
                    error = null,
                    nextAttemptAt = null,
                    progress = TaskProgress(
                        percent = 100,
                        message = current.progress?.message,
                        updatedAt = completedAt
                    )
                )
            val persisted = repository.update(completed)
            runCatching { checkpointRepository?.deleteByTaskId(taskId) }
            persisted
        } catch (timeout: TimeoutCancellationException) {
            val occurredAt = Instant.now(clock)
            handleFailure(
                task = task,
                taskError = TaskError(
                    code = "TASK_TIMEOUT",
                    category = TaskErrorCategory.TIMEOUT,
                    message = "Task timed out after ${task.timeout?.toMillis()} ms",
                    retryable = true,
                    details = mapOf("timeoutMs" to task.timeout?.toMillis().toString()),
                    occurredAt = occurredAt
                ),
                terminalStatus = TaskStatus.TIMED_OUT
            )
        } catch (requested: TaskCancellationRequestedException) {
            finishCancellation(loadCurrent(task))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val occurredAt = Instant.now(clock)
            handleFailure(
                task = task,
                taskError = errorClassifier.classify(error, occurredAt),
                terminalStatus = TaskStatus.FAILED
            )
        }
    }

    private fun handleFailure(
        task: Task,
        taskError: TaskError,
        terminalStatus: TaskStatus
    ): Task {
        val current = loadCurrent(task)
        if (current.cancellationRequest != null) {
            return finishCancellation(current)
        }

        val now = transitionTime(current)
        if (!taskError.retryable || !current.retryPolicy.canRetry(current.attemptCount)) {
            return finishFailure(current, taskError, terminalStatus, now)
        }

        val retryAt = now.plus(
            current.retryPolicy.delayAfterFailure(current.attemptCount)
        )
        val retrying = current
            .transitionTo(TaskStatus.RETRYING, now)
            .copy(
                error = taskError,
                nextAttemptAt = retryAt
            )
        return repository.update(retrying)
    }

    private fun finishFailure(
        task: Task,
        taskError: TaskError,
        terminalStatus: TaskStatus,
        at: Instant = Instant.now(clock)
    ): Task {
        val transitionAt = maxOf(at, task.updatedAt)
        val failed = task
            .transitionTo(terminalStatus, transitionAt)
            .copy(
                error = taskError,
                nextAttemptAt = null
            )
        return repository.update(failed)
    }

    private fun finishCancellation(task: Task): Task {
        if (task.status == TaskStatus.CANCELLED) {
            return task
        }
        val cancelled = task
            .transitionTo(TaskStatus.CANCELLED, transitionTime(task))
            .copy(error = null, nextAttemptAt = null)
        return repository.update(cancelled)
    }

    private fun loadCurrent(task: Task): Task {
        val taskId = requireNotNull(task.id) { "A persisted task must have an ID." }
        return repository.findById(taskId)
            ?: throw IllegalArgumentException("Task not found: $taskId")
    }

    private fun transitionTime(task: Task): Instant = maxOf(Instant.now(clock), task.updatedAt)

}
