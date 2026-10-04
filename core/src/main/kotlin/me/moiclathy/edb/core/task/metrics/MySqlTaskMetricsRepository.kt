package me.moiclathy.edb.core.task.metrics

import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/** Executes a single aggregate query without materializing task rows. */
class MySqlTaskMetricsRepository : TaskMetricsRepository {
    override fun snapshot(collectedAt: Instant): TaskRuntimeMetrics = transaction {
        var metrics: TaskRuntimeMetrics? = null
        exec(AGGREGATE_SQL) { result ->
            check(result.next()) { "Task metrics query returned no result." }
            val queued = result.getLong("queued_count")
            val retrying = result.getLong("retrying_count")
            metrics = TaskRuntimeMetrics(
                queueDepth = queued + retrying,
                queuedCount = queued,
                retryingCount = retrying,
                runningCount = result.getLong("running_count"),
                succeededCount = result.getLong("succeeded_count"),
                failedCount = result.getLong("failed_count"),
                timedOutCount = result.getLong("timed_out_count"),
                averageExecutionDurationMs = result
                    .getObject("average_execution_duration_ms")
                    ?.let { (it as Number).toDouble() },
                collectedAt = collectedAt
            )
        }
        checkNotNull(metrics) { "Failed to read task runtime metrics." }
    }

    private companion object {
        val AGGREGATE_SQL = """
            SELECT
                COALESCE(SUM(CASE WHEN status = 'QUEUED' THEN 1 ELSE 0 END), 0) AS queued_count,
                COALESCE(SUM(CASE WHEN status = 'RETRYING' THEN 1 ELSE 0 END), 0) AS retrying_count,
                COALESCE(SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END), 0) AS running_count,
                COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS succeeded_count,
                COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed_count,
                COALESCE(SUM(CASE WHEN status = 'TIMED_OUT' THEN 1 ELSE 0 END), 0) AS timed_out_count,
                AVG(
                    CASE
                        WHEN started_at IS NOT NULL AND completed_at IS NOT NULL
                        THEN TIMESTAMPDIFF(MICROSECOND, started_at, completed_at) / 1000.0
                        ELSE NULL
                    END
                ) AS average_execution_duration_ms
            FROM tasks
        """.trimIndent()
    }
}
