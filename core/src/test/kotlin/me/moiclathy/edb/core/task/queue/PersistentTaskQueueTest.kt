package me.moiclathy.edb.core.task.queue

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskPriority
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.repository.TaskRepository
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class PersistentTaskQueueTest {

    @Test
    fun `enqueue persists queued task without changing priority`() {
        val repository = mockk<TaskRepository>()
        val captured = slot<Task>()
        every { repository.save(capture(captured)) } answers { captured.captured }
        val queue = PersistentTaskQueue(repository)
        val createdAt = Instant.parse("2026-08-09T10:00:00Z")
        val queuedAt = Instant.parse("2026-08-09T10:00:01Z")
        val pending = Task.pending(
            projectId = 4,
            taskType = "configuration-export",
            configSnapshot = null,
            triggerUserId = 9,
            createdAt = createdAt,
            priority = TaskPriority.HIGH
        )

        val queued = queue.enqueue(pending, queuedAt)

        assertEquals(TaskStatus.QUEUED, queued.status)
        assertEquals(TaskPriority.HIGH, queued.priority)
        assertEquals(queuedAt, queued.updatedAt)
        verify(exactly = 1) { repository.save(any()) }
    }
}
