package me.moiclathy.edb.core.task.execution

import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.checkpoint.TaskCheckpoint

interface TaskExecutionContext {
    suspend fun ensureNotCancelled()

    suspend fun reportProgress(percent: Int, message: String? = null): TaskProgress

    /** Checkpoint loaded for this attempt, or null when starting from the beginning. */
    suspend fun loadCheckpoint(): TaskCheckpoint? = null

    /** Available only to handlers that explicitly implement [CheckpointTaskHandler]. */
    suspend fun saveCheckpoint(
        cursor: String,
        artifacts: Map<String, String> = emptyMap(),
        state: Map<String, String> = emptyMap()
    ): TaskCheckpoint = throw UnsupportedOperationException("This task handler does not support checkpoints.")
}
