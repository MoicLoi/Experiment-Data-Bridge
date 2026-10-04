package me.moiclathy.edb.core.task.error

import java.time.Instant

data class TaskError(
    val code: String,
    val category: TaskErrorCategory,
    val message: String,
    val retryable: Boolean,
    val details: Map<String, String> = emptyMap(),
    val occurredAt: Instant
) {
    init {
        require(code.isNotBlank() && code.length <= MAX_CODE_LENGTH) {
            "Task error code must not be blank or exceed $MAX_CODE_LENGTH characters."
        }
        require(message.isNotBlank()) { "Task error message must not be blank." }
    }

    companion object {
        const val MAX_CODE_LENGTH = 100
    }
}
