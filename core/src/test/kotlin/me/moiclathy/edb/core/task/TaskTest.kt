package me.moiclathy.edb.core.task

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TaskTest {

    @Test
    fun `task follows valid lifecycle and rejects transition from terminal state`() {
        val createdAt = Instant.parse("2026-08-09T10:00:00Z")
        val startedAt = Instant.parse("2026-08-09T10:01:00Z")
        val completedAt = Instant.parse("2026-08-09T10:02:00Z")

        val pending = Task.pending(
            projectId = 12,
            taskType = "configuration-export",
            configSnapshot = "version: 2",
            triggerUserId = 7,
            createdAt = createdAt
        )
        val queued = pending.transitionTo(TaskStatus.QUEUED, createdAt)
        val running = queued.transitionTo(TaskStatus.RUNNING, startedAt)
        val completed = running.transitionTo(TaskStatus.COMPLETED, completedAt)

        assertNull(queued.startedAt)
        assertEquals(startedAt, running.startedAt)
        assertEquals(completedAt, completed.completedAt)
        assertTrue(completed.status.isTerminal)
        assertFailsWith<IllegalArgumentException> {
            completed.transitionTo(TaskStatus.RUNNING, completedAt)
        }
    }
}
