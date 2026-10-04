package me.moiclathy.edb.core.task.progress

import java.time.Instant

data class TaskProgress(
    val percent: Int,
    val message: String?,
    val updatedAt: Instant
) {
    init {
        require(percent in 0..100) { "Task progress must be between 0 and 100." }
        require(message == null || message.length <= MAX_MESSAGE_LENGTH) {
            "Task progress message must not exceed $MAX_MESSAGE_LENGTH characters."
        }
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 500
    }
}
