package me.moiclathy.edb.core.task.execution

import me.moiclathy.edb.core.task.Task

/**
 * Opt-in recovery contract. Handlers that do not implement it always retry from the beginning.
 * A changed version invalidates checkpoints written by an older implementation.
 */
interface CheckpointTaskHandler : TaskHandler {
    val handlerVersion: String

    fun supportsCheckpoint(task: Task): Boolean = true
}
