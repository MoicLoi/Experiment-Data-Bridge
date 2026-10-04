package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionEstimate
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionManagerState
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionPolicy
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionResult

@Serializable
data class DataFileRetentionPolicyResponse(
    val enabled: Boolean,
    val retentionDays: Long,
    val cleanupIntervalMinutes: Long,
    val batchSize: Int,
    val maxBatchesPerRun: Int
) {
    companion object {
        fun from(policy: DataFileRetentionPolicy) = DataFileRetentionPolicyResponse(
            policy.enabled,
            policy.retentionPeriod.toDays(),
            policy.cleanupInterval.toMinutes(),
            policy.batchSize,
            policy.maxBatchesPerRun
        )
    }
}

@Serializable
data class DataFileRetentionResultResponse(
    val cutoff: String,
    val deletedRecords: Int,
    val completedBatches: Int,
    val completedAt: String
) {
    companion object {
        fun from(result: DataFileRetentionResult) = DataFileRetentionResultResponse(
            result.cutoff.toString(),
            result.deletedRecords,
            result.completedBatches,
            result.completedAt.toString()
        )
    }
}

@Serializable
data class DataFileRetentionStateResponse(
    val running: Boolean,
    val lastStartedAt: String?,
    val lastResult: DataFileRetentionResultResponse?,
    val lastFailure: String?
) {
    companion object {
        fun from(state: DataFileRetentionManagerState) = DataFileRetentionStateResponse(
            state.running,
            state.lastStartedAt?.toString(),
            state.lastResult?.let(DataFileRetentionResultResponse::from),
            state.lastFailure
        )
    }
}

@Serializable
data class DataFileRetentionOverviewResponse(
    val policy: DataFileRetentionPolicyResponse,
    val state: DataFileRetentionStateResponse
)

@Serializable
data class DataFileRetentionEstimateResponse(
    val cutoff: String,
    val expiredDeletedFiles: Long,
    val collectedAt: String
) {
    companion object {
        fun from(estimate: DataFileRetentionEstimate) = DataFileRetentionEstimateResponse(
            estimate.cutoff.toString(),
            estimate.expiredDeletedFiles,
            estimate.collectedAt.toString()
        )
    }
}
