package me.moiclathy.edb.core.task.event

enum class TaskLifecycleEventType {
    CREATED,
    ENQUEUED,
    CLAIMED,
    PROGRESS_UPDATED,
    RETRY_SCHEDULED,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    CANCELLATION_REQUESTED,
    CANCELLED
}
