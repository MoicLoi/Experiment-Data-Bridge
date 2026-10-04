package me.moiclathy.edb.core.task

/**
 * Lifecycle state persisted for a task.
 *
 * Existing status names are retained for database compatibility. New states
 * prepare the model for queueing, retry, timeout, and cancellation without
 * tying it to a particular executor implementation.
 */
enum class TaskStatus {
    PENDING,
    QUEUED,
    RUNNING,
    RETRYING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    CANCELLED;

    val isTerminal: Boolean
        get() = this in TERMINAL_STATUSES

    fun canTransitionTo(next: TaskStatus): Boolean =
        next in ALLOWED_TRANSITIONS.getValue(this)

    companion object {
        private val TERMINAL_STATUSES = setOf(
            COMPLETED,
            FAILED,
            TIMED_OUT,
            CANCELLED
        )

        private val ALLOWED_TRANSITIONS = mapOf(
            PENDING to setOf(QUEUED, CANCELLED),
            QUEUED to setOf(RUNNING, CANCELLED),
            RUNNING to setOf(COMPLETED, FAILED, RETRYING, TIMED_OUT, CANCELLED),
            RETRYING to setOf(QUEUED, CANCELLED),
            COMPLETED to emptySet(),
            FAILED to setOf(QUEUED),
            TIMED_OUT to setOf(QUEUED),
            CANCELLED to emptySet()
        )
    }
}
