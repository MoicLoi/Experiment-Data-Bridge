package me.moiclathy.edb.core.task.audit

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.retry.RetryPolicy
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class TaskAuditEventFactoryTest {

    @Test
    fun `lifecycle statuses map to audit event types`() {
        val createdAt = Instant.parse("2026-08-10T10:00:00Z")
        val base = Task(
            id = 41,
            projectId = 5,
            taskType = "audit-task",
            status = TaskStatus.PENDING,
            triggerUserId = 8,
            createdAt = createdAt,
            updatedAt = createdAt
        )
        val expected = mapOf(
            TaskStatus.QUEUED to TaskAuditEventType.ENQUEUED,
            TaskStatus.RUNNING to TaskAuditEventType.CLAIMED,
            TaskStatus.RETRYING to TaskAuditEventType.RETRY_SCHEDULED,
            TaskStatus.COMPLETED to TaskAuditEventType.COMPLETED,
            TaskStatus.FAILED to TaskAuditEventType.FAILED,
            TaskStatus.TIMED_OUT to TaskAuditEventType.TIMED_OUT,
            TaskStatus.CANCELLED to TaskAuditEventType.CANCELLED
        )

        assertEquals(TaskAuditEventType.CREATED, TaskAuditEventFactory.created(base).type)
        expected.forEach { (status, eventType) ->
            val beforeStatus = when (status) {
                TaskStatus.QUEUED -> TaskStatus.PENDING
                TaskStatus.RUNNING -> TaskStatus.QUEUED
                else -> TaskStatus.RUNNING
            }
            val before = base.copy(status = beforeStatus)
            val after = base.copy(
                status = status,
                updatedAt = createdAt.plusSeconds(1)
            )
            assertEquals(
                eventType,
                TaskAuditEventFactory.transition(before, after)?.type
            )
        }
        assertEquals(
            TaskAuditEventType.CANCELLATION_REQUESTED,
            TaskAuditEventFactory.cancellationRequested(
                task = base,
                requestedBy = 8,
                requestedAt = createdAt.plusSeconds(1)
            ).type
        )

        val failed = base.copy(
            status = TaskStatus.FAILED,
            attemptCount = 3,
            retryPolicy = RetryPolicy(maxAttempts = 3)
        )
        val requeued = failed.copy(
            status = TaskStatus.QUEUED,
            attemptCount = 0,
            updatedAt = createdAt.plusSeconds(2)
        )
        val manualRetry = TaskAuditEventFactory.manuallyRequeued(failed, requeued, 99)
        assertEquals(TaskAuditEventType.MANUALLY_REQUEUED, manualRetry.type)
        assertEquals(TaskStatus.FAILED, manualRetry.fromStatus)
        assertEquals(TaskStatus.QUEUED, manualRetry.toStatus)
        assertEquals(99, manualRetry.actorUserId)
        assertEquals("3", manualRetry.details["previousAttemptCount"])
    }
}
