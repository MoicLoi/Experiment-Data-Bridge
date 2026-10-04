package me.moiclathy.edb.core.task.runtime

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.moiclathy.edb.core.event.CoroutineEventBus
import me.moiclathy.edb.core.event.subscribe
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.cancellation.TaskCancellationService
import me.moiclathy.edb.core.task.event.TaskLifecycleEvent
import me.moiclathy.edb.core.task.event.TaskLifecycleEventType
import me.moiclathy.edb.core.task.execution.TaskExecutor
import me.moiclathy.edb.core.task.queue.TaskQueue
import me.moiclathy.edb.core.task.repository.TaskRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TaskRuntimeTest {

    @Test
    fun `submit and run publish lifecycle events in order`() = runBlocking {
        val queue = mockk<TaskQueue>()
        val executor = mockk<TaskExecutor>()
        val repository = mockk<TaskRepository>()
        val cancellationService = mockk<TaskCancellationService>()
        val eventBus = CoroutineEventBus(dispatcher = Dispatchers.Default)
        val received = Collections.synchronizedList(mutableListOf<TaskLifecycleEventType>())
        val allReceived = CompletableDeferred<Unit>()
        eventBus.subscribe<TaskLifecycleEvent> { event ->
            received += event.type
            if (received.size == 4) allReceived.complete(Unit)
        }
        val createdAt = Instant.parse("2026-08-10T10:00:00Z")
        val pending = Task.pending(
            projectId = 6,
            taskType = "runtime-task",
            configSnapshot = null,
            triggerUserId = 7,
            createdAt = createdAt
        )
        val queued = pending.copy(
            id = 61,
            status = TaskStatus.QUEUED,
            updatedAt = createdAt.plusSeconds(1)
        )
        val running = queued.copy(
            status = TaskStatus.RUNNING,
            attemptCount = 1,
            startedAt = createdAt.plusSeconds(2),
            updatedAt = createdAt.plusSeconds(2)
        )
        val completed = running.copy(
            status = TaskStatus.COMPLETED,
            completedAt = createdAt.plusSeconds(3),
            updatedAt = createdAt.plusSeconds(3)
        )
        every { queue.enqueue(pending, any()) } returns queued
        every { queue.claimNext(any()) } returns running
        coEvery { executor.execute(running) } returns completed
        val runtime = TaskRuntime(
            queue = queue,
            executor = executor,
            repository = repository,
            cancellationService = cancellationService,
            eventPublisher = TaskLifecycleEventPublisher(eventBus),
            clock = Clock.fixed(createdAt.plusSeconds(1), ZoneOffset.UTC)
        )

        assertEquals(queued, runtime.submit(pending))
        assertTrue(runtime.runOnce())
        withTimeout(2_000) { allReceived.await() }

        assertEquals(
            listOf(
                TaskLifecycleEventType.CREATED,
                TaskLifecycleEventType.ENQUEUED,
                TaskLifecycleEventType.CLAIMED,
                TaskLifecycleEventType.COMPLETED
            ),
            received.toList()
        )
        eventBus.close()
    }
}
