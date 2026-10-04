package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.task.metrics.TaskRuntimeMetrics

@Serializable
data class TaskRuntimeMetricsResponse(
    val queueDepth: Long,
    val queuedCount: Long,
    val retryingCount: Long,
    val runningCount: Long,
    val succeededCount: Long,
    val failedCount: Long,
    val timedOutCount: Long,
    val averageExecutionDurationMs: Double?,
    val collectedAt: String
) {
    companion object {
        fun from(metrics: TaskRuntimeMetrics) = TaskRuntimeMetricsResponse(
            queueDepth = metrics.queueDepth,
            queuedCount = metrics.queuedCount,
            retryingCount = metrics.retryingCount,
            runningCount = metrics.runningCount,
            succeededCount = metrics.succeededCount,
            failedCount = metrics.failedCount,
            timedOutCount = metrics.timedOutCount,
            averageExecutionDurationMs = metrics.averageExecutionDurationMs,
            collectedAt = metrics.collectedAt.toString()
        )
    }
}
