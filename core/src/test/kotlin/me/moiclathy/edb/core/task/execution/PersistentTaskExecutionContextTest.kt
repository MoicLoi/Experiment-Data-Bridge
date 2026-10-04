package me.moiclathy.edb.core.task.execution

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import me.moiclathy.edb.core.event.CoroutineEventBus
import me.moiclathy.edb.core.event.subscribe
import me.moiclathy.edb.core.task.event.TaskLifecycleEvent
import me.moiclathy.edb.core.task.event.TaskLifecycleEventType
import me.moiclathy.edb.core.task.runtime.TaskLifecycleEventPublisher
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.repository.TaskRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class PersistentTaskExecutionContextTest {

    @Test
    fun `report progress persists timestamped snapshot`() = runBlocking {
        val repository = mockk<TaskRepository>()
        val progressSlot = slot<TaskProgress>()
        val running = Task(
            id = 24,
            projectId = 4,
            taskType = "progress-task",
            status = TaskStatus.RUNNING,
            attemptCount = 1,
            triggerUserId = 9,
            startedAt = Instant.parse("2026-08-09T10:01:00Z"),
            createdAt = Instant.parse("2026-08-09T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-09T10:01:00Z")
        )
        every { repository.findById(24) } returns running
        every { repository.updateProgress(24, capture(progressSlot)) } answers {
            running.copy(
                progress = progressSlot.captured,
                updatedAt = progressSlot.captured.updatedAt
            )
        }
        val progressTime = Instant.parse("2026-08-09T10:03:00Z")
        val eventBus = CoroutineEventBus()
        val published = CompletableDeferred<TaskLifecycleEvent>()
        val subscription = eventBus.subscribe<TaskLifecycleEvent> { published.complete(it) }
        val context = PersistentTaskExecutionContext(
            taskId = 24,
            repository = repository,
            clock = Clock.fixed(progressTime, ZoneOffset.UTC),
            eventPublisher = TaskLifecycleEventPublisher(eventBus)
        )

        try {
            val progress = context.reportProgress(35, "Loading data")
            val event = withTimeout(2_000) { published.await() }

            assertEquals(35, progress.percent)
            assertEquals("Loading data", progress.message)
            assertEquals(progressTime, progress.updatedAt)
            assertEquals(TaskLifecycleEventType.PROGRESS_UPDATED, event.type)
            assertEquals(4, event.projectId)
            assertEquals("35", event.details["progressPercent"])
        } finally {
            subscription.cancel()
            eventBus.close()
        }
    }
}
