package me.moiclathy.edb.core.task.metrics

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class TaskMetricsApplicationServiceTest {
    @Test
    fun `returns repository snapshot at current clock time`() {
        val collectedAt = Instant.parse("2026-08-10T14:00:00Z")
        val repository = object : TaskMetricsRepository {
            override fun snapshot(collectedAt: Instant) = TaskRuntimeMetrics(
                queueDepth = 5,
                queuedCount = 3,
                retryingCount = 2,
                runningCount = 4,
                succeededCount = 20,
                failedCount = 3,
                timedOutCount = 1,
                averageExecutionDurationMs = 1250.5,
                collectedAt = collectedAt
            )
        }
        val service = TaskMetricsApplicationService(
            repository,
            Clock.fixed(collectedAt, ZoneOffset.UTC)
        )

        val metrics = service.snapshot()

        assertEquals(5, metrics.queueDepth)
        assertEquals(4, metrics.runningCount)
        assertEquals(1250.5, metrics.averageExecutionDurationMs)
        assertEquals(collectedAt, metrics.collectedAt)
    }
}
