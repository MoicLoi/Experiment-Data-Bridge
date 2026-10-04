package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.task.retention.TaskCleanupResult
import me.moiclathy.edb.core.task.retention.TaskRetentionEstimate
import me.moiclathy.edb.core.task.retention.TaskRetentionManagerState
import me.moiclathy.edb.core.task.retention.TaskRetentionPolicy

@Serializable
data class TaskRetentionPolicyResponse(
    val enabled: Boolean,
    val retentionDays: Long,
    val cleanupIntervalMinutes: Long,
    val batchSize: Int,
    val maxBatchesPerRun: Int
) {
    companion object {
        fun from(policy: TaskRetentionPolicy) = TaskRetentionPolicyResponse(
            enabled = policy.enabled,
            retentionDays = policy.retentionPeriod.toDays(),
            cleanupIntervalMinutes = policy.cleanupInterval.toMinutes(),
            batchSize = policy.batchSize,
            maxBatchesPerRun = policy.maxBatchesPerRun
        )
    }
}

@Serializable
data class TaskCleanupResultResponse(
    val cutoff: String,
    val deletedTasks: Int,
    val completedBatches: Int,
    val completedAt: String
) {
    companion object {
        fun from(result: TaskCleanupResult) = TaskCleanupResultResponse(
            cutoff = result.cutoff.toString(),
            deletedTasks = result.deletedTasks,
            completedBatches = result.completedBatches,
            completedAt = result.completedAt.toString()
        )
    }
}

@Serializable
data class TaskRetentionStateResponse(
    val running: Boolean,
    val lastStartedAt: String?,
    val lastResult: TaskCleanupResultResponse?,
    val lastFailure: String?
) {
    companion object {
        fun from(state: TaskRetentionManagerState) = TaskRetentionStateResponse(
            running = state.running,
            lastStartedAt = state.lastStartedAt?.toString(),
            lastResult = state.lastResult?.let(TaskCleanupResultResponse::from),
            lastFailure = state.lastFailure
        )
    }
}

@Serializable
data class TaskRetentionOverviewResponse(
    val policy: TaskRetentionPolicyResponse,
    val state: TaskRetentionStateResponse
)

@Serializable
data class TaskRetentionEstimateResponse(
    val cutoff: String,
    val expiredTasks: Long,
    val collectedAt: String
) {
    companion object {
        fun from(estimate: TaskRetentionEstimate) = TaskRetentionEstimateResponse(
            cutoff = estimate.cutoff.toString(),
            expiredTasks = estimate.expiredTasks,
            collectedAt = estimate.collectedAt.toString()
        )
    }
}
