package me.moiclathy.edb.core.task.metrics

import java.time.Instant

data class TaskRuntimeMetrics(
    val queueDepth: Long,
    val queuedCount: Long,
    val retryingCount: Long,
    val runningCount: Long,
    val succeededCount: Long,
    val failedCount: Long,
    val timedOutCount: Long,
    val averageExecutionDurationMs: Double?,
    val collectedAt: Instant
) {
    init {
        require(
            listOf(
                queueDepth,
                queuedCount,
                retryingCount,
                runningCount,
                succeededCount,
                failedCount,
                timedOutCount
            ).all { it >= 0 }
        ) { "Task metric counts must not be negative." }
        require(averageExecutionDurationMs == null || averageExecutionDurationMs >= 0) {
            "Average task execution duration must not be negative."
        }
    }
}
