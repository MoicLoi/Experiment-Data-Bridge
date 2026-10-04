package me.moiclathy.edb.core.task.audit

enum class TaskAuditEventType {
    CREATED,
    ENQUEUED,
    CLAIMED,
    RETRY_SCHEDULED,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    MANUALLY_REQUEUED,
    CANCELLATION_REQUESTED,
    CANCELLED
}
