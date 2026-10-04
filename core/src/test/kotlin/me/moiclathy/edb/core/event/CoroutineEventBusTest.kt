package me.moiclathy.edb.core.event

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.event.TaskLifecycleEvent
import me.moiclathy.edb.core.task.event.TaskLifecycleEventType
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CoroutineEventBusTest {

    @Test
    fun `subscribers are isolated and can be cancelled with the bus`() = runBlocking {
        val subscriberFailures = AtomicInteger(0)
        val healthyCalls = AtomicInteger(0)
        val failingCalls = AtomicInteger(0)
        val healthyReceivedSecond = CompletableDeferred<Unit>()
        val failingRecovered = CompletableDeferred<Unit>()
        val bus = CoroutineEventBus(
            dispatcher = Dispatchers.Default,
            bufferCapacity = 4,
            subscriberErrorHandler = EventSubscriberErrorHandler { _, _, _ ->
                subscriberFailures.incrementAndGet()
            }
        )
        val healthy = bus.subscribe<TaskLifecycleEvent> {
            if (healthyCalls.incrementAndGet() == 2) {
                healthyReceivedSecond.complete(Unit)
            }
        }
        val failing = bus.subscribe<TaskLifecycleEvent> {
            if (failingCalls.incrementAndGet() == 1) {
                error("subscriber failure")
            }
            failingRecovered.complete(Unit)
        }
        val event = TaskLifecycleEvent(
            taskId = 51,
            projectId = 7,
            type = TaskLifecycleEventType.ENQUEUED,
            previousStatus = TaskStatus.PENDING,
            status = TaskStatus.QUEUED,
            attemptCount = 0,
            occurredAt = Instant.parse("2026-08-10T12:00:00Z")
        )

        assertEquals(2, bus.publish(event).acceptedSubscribers)
        assertEquals(2, bus.publish(event).acceptedSubscribers)
        withTimeout(2_000) {
            healthyReceivedSecond.await()
            failingRecovered.await()
        }

        assertEquals(1, subscriberFailures.get())
        assertEquals(2, healthyCalls.get())
        assertEquals(2, failingCalls.get())
        assertTrue(failing.isActive)

        healthy.cancel()
        assertFalse(healthy.isActive)
        assertEquals(1, bus.publish(event).matchedSubscribers)

        bus.close()
        assertTrue(bus.isClosed)
        assertFalse(failing.isActive)
        var rejectedAfterClose = false
        try {
            bus.publish(event)
        } catch (_: IllegalStateException) {
            rejectedAfterClose = true
        }
        assertTrue(rejectedAfterClose)
    }
}
