package me.moiclathy.edb.core.task.checkpoint

import java.time.Instant

/** Durable recovery state owned by one task handler version. */
data class TaskCheckpoint(
    val taskId: Int,
    val handlerVersion: String,
    val cursor: String,
    val artifacts: Map<String, String> = emptyMap(),
    val state: Map<String, String> = emptyMap(),
    val updatedAt: Instant
) {
    init {
        require(taskId > 0) { "Checkpoint task ID must be positive." }
        require(handlerVersion.isNotBlank()) { "Checkpoint handler version must not be blank." }
        require(cursor.isNotBlank()) { "Checkpoint cursor must not be blank." }
        require(artifacts.keys.none(String::isBlank)) { "Checkpoint artifact names must not be blank." }
        require(state.keys.none(String::isBlank)) { "Checkpoint state names must not be blank." }
    }
}
