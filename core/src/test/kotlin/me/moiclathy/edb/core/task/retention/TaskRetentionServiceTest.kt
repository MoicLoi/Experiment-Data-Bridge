package me.moiclathy.edb.core.task.retention

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TaskRetentionServiceTest {
    @Test
    fun `deletes expired tasks in bounded batches using retention cutoff`() {
        val calls = mutableListOf<Pair<Instant, Int>>()
        val deletions = ArrayDeque(listOf(2, 2, 1))
        val repository = object : TaskRetentionRepository {
            override fun countExpired(cutoff: Instant): Long = 5

            override fun deleteExpired(cutoff: Instant, limit: Int): Int {
                calls += cutoff to limit
                return deletions.removeFirst()
            }
        }
        val now = Instant.parse("2026-08-10T16:00:00Z")
        val service = TaskRetentionService(
            repository = repository,
            policy = TaskRetentionPolicy(
                enabled = true,
                retentionPeriod = Duration.ofDays(30),
                cleanupInterval = Duration.ofHours(1),
                batchSize = 2,
                maxBatchesPerRun = 5
            ),
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )

        val result = service.cleanup()
        val estimate = service.estimate()

        assertEquals(5, result.deletedTasks)
        assertEquals(3, result.completedBatches)
        assertEquals(now.minus(Duration.ofDays(30)), result.cutoff)
        assertEquals(listOf(2, 2, 2), calls.map { it.second })
        assertEquals(5, estimate.expiredTasks)
        assertEquals(result.cutoff, estimate.cutoff)
    }

    @Test
    fun `manager runs immediately only when retention is enabled`() {
        val invoked = CountDownLatch(1)
        val repository = object : TaskRetentionRepository {
            override fun countExpired(cutoff: Instant): Long = 0

            override fun deleteExpired(cutoff: Instant, limit: Int): Int {
                invoked.countDown()
                return 0
            }
        }
        val enabledPolicy = TaskRetentionPolicy(
            enabled = true,
            cleanupInterval = Duration.ofHours(1)
        )
        val service = TaskRetentionService(repository, enabledPolicy)
        val enabled = TaskRetentionManager(service, enabledPolicy)
        val disabled = TaskRetentionManager(
            service,
            enabledPolicy.copy(enabled = false)
        )

        try {
            assertTrue(enabled.start())
            assertTrue(invoked.await(2, TimeUnit.SECONDS))
            assertFalse(disabled.start())
        } finally {
            enabled.close()
            disabled.close()
        }
    }

    @Test
    fun `manual cleanup is retained as latest manager result`() = runBlocking {
        val repository = object : TaskRetentionRepository {
            override fun countExpired(cutoff: Instant): Long = 0
            override fun deleteExpired(cutoff: Instant, limit: Int): Int = 0
        }
        val policy = TaskRetentionPolicy(enabled = false)
        val manager = TaskRetentionManager(
            TaskRetentionService(repository, policy),
            policy
        )

        val result = manager.runNow()

        assertEquals(result, manager.state.value.lastResult)
        assertFalse(manager.state.value.running)
        manager.close()
    }
}
