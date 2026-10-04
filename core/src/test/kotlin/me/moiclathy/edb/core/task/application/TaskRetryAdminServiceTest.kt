package me.moiclathy.edb.core.task.application

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.moiclathy.edb.core.event.CoroutineEventBus
import me.moiclathy.edb.core.event.subscribe
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.event.TaskLifecycleEvent
import me.moiclathy.edb.core.task.event.TaskLifecycleEventType
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.runtime.TaskLifecycleEventPublisher
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class TaskRetryAdminServiceTest {
    @Test
    fun `requeues failed task and publishes queued lifecycle event`() = runBlocking {
        val repository = mockk<TaskRepository>()
        val failed = task(TaskStatus.FAILED)
        val requeued = task(TaskStatus.QUEUED).copy(attemptCount = 0)
        val at = Instant.parse("2026-08-10T15:00:00Z")
        every { repository.findById(91) } returns failed
        every { repository.requeueFailed(91, 12, at) } returns requeued
        val bus = CoroutineEventBus()
        val received = CompletableDeferred<TaskLifecycleEvent>()
        val subscription = bus.subscribe<TaskLifecycleEvent> { received.complete(it) }
        val service = TaskRetryAdminService(
            repository,
            TaskLifecycleEventPublisher(bus),
            Clock.fixed(at, ZoneOffset.UTC)
        )

        try {
            val result = service.requeue(91, 12)
            val event = withTimeout(2_000) { received.await() }

            assertEquals(TaskStatus.QUEUED, result.status)
            assertEquals(TaskLifecycleEventType.ENQUEUED, event.type)
            assertEquals(TaskStatus.FAILED, event.previousStatus)
        } finally {
            subscription.cancel()
            bus.close()
        }
    }

    private fun task(status: TaskStatus) = Task(
        id = 91,
        projectId = 7,
        taskType = "pipeline",
        status = status,
        attemptCount = if (status == TaskStatus.FAILED) 1 else 0,
        triggerUserId = 11,
        createdAt = Instant.parse("2026-08-10T14:00:00Z"),
        updatedAt = Instant.parse("2026-08-10T14:30:00Z")
    )
}
