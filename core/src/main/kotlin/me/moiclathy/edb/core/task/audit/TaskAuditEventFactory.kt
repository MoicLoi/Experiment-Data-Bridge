package me.moiclathy.edb.core.task.audit

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import java.time.Instant

object TaskAuditEventFactory {

    fun created(task: Task): TaskAuditEvent = TaskAuditEvent(
        taskId = requireNotNull(task.id),
        type = TaskAuditEventType.CREATED,
        fromStatus = null,
        toStatus = TaskStatus.PENDING,
        actorUserId = task.triggerUserId,
        details = mapOf(
            "taskType" to task.taskType,
            "priority" to task.priority.value.toString()
        ),
        createdAt = task.createdAt
    )

    fun transition(
        before: Task,
        after: Task,
        actorUserId: Int? = null
    ): TaskAuditEvent? {
        if (before.status == after.status) return null

        val type = when (after.status) {
            TaskStatus.QUEUED -> TaskAuditEventType.ENQUEUED
            TaskStatus.RUNNING -> TaskAuditEventType.CLAIMED
            TaskStatus.RETRYING -> TaskAuditEventType.RETRY_SCHEDULED
            TaskStatus.COMPLETED -> TaskAuditEventType.COMPLETED
            TaskStatus.FAILED -> TaskAuditEventType.FAILED
            TaskStatus.TIMED_OUT -> TaskAuditEventType.TIMED_OUT
            TaskStatus.CANCELLED -> TaskAuditEventType.CANCELLED
            TaskStatus.PENDING -> return null
        }

        return TaskAuditEvent(
            taskId = requireNotNull(after.id),
            type = type,
            fromStatus = before.status,
            toStatus = after.status,
            actorUserId = actorUserId,
            details = buildDetails(after),
            createdAt = after.updatedAt
        )
    }

    fun cancellationRequested(
        task: Task,
        requestedBy: Int,
        requestedAt: Instant
    ): TaskAuditEvent = TaskAuditEvent(
        taskId = requireNotNull(task.id),
        type = TaskAuditEventType.CANCELLATION_REQUESTED,
        fromStatus = task.status,
        toStatus = task.status,
        actorUserId = requestedBy,
        createdAt = requestedAt
    )

    fun manuallyRequeued(
        before: Task,
        after: Task,
        requestedBy: Int
    ): TaskAuditEvent {
        require(before.status in setOf(TaskStatus.FAILED, TaskStatus.TIMED_OUT)) {
            "Only failed or timed-out tasks can be manually requeued."
        }
        require(after.status == TaskStatus.QUEUED) {
            "A manually requeued task must be queued."
        }
        return TaskAuditEvent(
            taskId = requireNotNull(after.id),
            type = TaskAuditEventType.MANUALLY_REQUEUED,
            fromStatus = before.status,
            toStatus = after.status,
            actorUserId = requestedBy,
            details = buildMap {
                put("previousAttemptCount", before.attemptCount.toString())
                before.error?.let {
                    put("previousErrorCode", it.code)
                    put("previousErrorCategory", it.category.name)
                }
            },
            createdAt = after.updatedAt
        )
    }

    private fun buildDetails(task: Task): Map<String, String> = buildMap {
        put("attemptCount", task.attemptCount.toString())
        task.nextAttemptAt?.let { put("nextAttemptAt", it.toString()) }
        task.result?.outputPath?.let { put("outputPath", it) }
        task.result?.metadata?.forEach { (key, value) ->
            if (key in AUDIT_RESULT_KEYS) put(key, value)
        }
        task.error?.let {
            put("errorCode", it.code)
            put("errorCategory", it.category.name)
        }
    }

    private val AUDIT_RESULT_KEYS = setOf("dataFileId", "provenanceReportDataFileId", "provenanceOperation", "inputRows", "outputRows", "affectedRows")
}
