package me.moiclathy.edb.core.task.scheduler

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.moiclathy.edb.core.task.runtime.TaskRunner
import me.moiclathy.edb.core.task.worker.TaskWorker
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoroutineTaskSchedulerTest {
    @Test
    fun `manages concurrency scales down and stops gracefully`() = runBlocking {
        val runner = ConcurrentRunner()
        val scheduler = CoroutineTaskScheduler(
            worker = TaskWorker(runner, pollInterval = Duration.ofMillis(5)),
            config = TaskSchedulerConfig(
                workerCount = 3,
                shutdownTimeout = Duration.ofSeconds(2)
            )
        )

        assertTrue(scheduler.start())
        assertFalse(scheduler.start())
        awaitCondition { runner.maximumConcurrency.get() >= 3 }
        assertEquals(TaskSchedulerStatus.RUNNING, scheduler.state.value.status)
        assertEquals(3, scheduler.state.value.activeWorkerCount)

        scheduler.updateWorkerCount(1)
        awaitCondition { scheduler.state.value.activeWorkerCount == 1 }
        assertEquals(1, scheduler.state.value.desiredWorkerCount)

        assertTrue(scheduler.stop())
        assertEquals(TaskSchedulerStatus.STOPPED, scheduler.state.value.status)
        assertEquals(0, scheduler.state.value.activeWorkerCount)
        assertFalse(scheduler.stop())
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(2_000) {
            while (!condition()) delay(5)
        }
    }

    private class ConcurrentRunner : TaskRunner {
        private val currentConcurrency = AtomicInteger()
        val maximumConcurrency = AtomicInteger()

        override suspend fun runOnce(): Boolean {
            val current = currentConcurrency.incrementAndGet()
            maximumConcurrency.updateAndGet { previous -> maxOf(previous, current) }
            try {
                delay(20)
            } finally {
                currentConcurrency.decrementAndGet()
            }
            return true
        }
    }
}
