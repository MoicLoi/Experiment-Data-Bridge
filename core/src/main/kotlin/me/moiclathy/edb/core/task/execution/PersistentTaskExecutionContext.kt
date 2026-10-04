package me.moiclathy.edb.core.task.execution

import me.moiclathy.edb.core.task.cancellation.TaskCancellationRequestedException
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.runtime.TaskLifecycleEventPublisher
import java.time.Clock
import java.time.Instant
import me.moiclathy.edb.core.task.checkpoint.TaskCheckpoint
import me.moiclathy.edb.core.task.checkpoint.TaskCheckpointRepository

class PersistentTaskExecutionContext(
    private val taskId: Int,
    private val repository: TaskRepository,
    private val clock: Clock,
    private val eventPublisher: TaskLifecycleEventPublisher? = null,
    private val checkpointRepository: TaskCheckpointRepository? = null,
    private val checkpointHandlerVersion: String? = null,
    private val initialCheckpoint: TaskCheckpoint? = null
) : TaskExecutionContext {
    private var currentCheckpoint: TaskCheckpoint? = initialCheckpoint

    override suspend fun ensureNotCancelled() {
        val task = repository.findById(taskId)
            ?: throw IllegalArgumentException("Task not found: $taskId")
        if (task.cancellationRequest != null || task.status.isTerminal) {
            throw TaskCancellationRequestedException(taskId)
        }
    }

    override suspend fun reportProgress(percent: Int, message: String?): TaskProgress {
        ensureNotCancelled()
        val progress = TaskProgress(
            percent = percent,
            message = message,
            updatedAt = Instant.now(clock)
        )
        val task = repository.updateProgress(taskId, progress)
        eventPublisher?.publishProgress(task)
        return requireNotNull(task.progress)
    }

    override suspend fun loadCheckpoint(): TaskCheckpoint? = currentCheckpoint

    override suspend fun saveCheckpoint(
        cursor: String,
        artifacts: Map<String, String>,
        state: Map<String, String>
    ): TaskCheckpoint {
        ensureNotCancelled()
        val repository = checkpointRepository
            ?: throw UnsupportedOperationException("Checkpoint persistence is not configured.")
        val version = checkpointHandlerVersion
            ?: throw UnsupportedOperationException("This task handler does not support checkpoints.")
        return repository.save(
            TaskCheckpoint(taskId, version, cursor, artifacts, state, Instant.now(clock))
        ).also { currentCheckpoint = it }
    }
}
