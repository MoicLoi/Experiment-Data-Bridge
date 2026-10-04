package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.datafile.lifecycle.DataFileOrphanCleanupEstimate
import me.moiclathy.edb.core.datafile.lifecycle.DataFileOrphanCleanupManagerState
import me.moiclathy.edb.core.datafile.lifecycle.DataFileOrphanCleanupPolicy
import me.moiclathy.edb.core.datafile.lifecycle.DataFileOrphanCleanupResult

@Serializable
data class DataFileOrphanCleanupPolicyResponse(
    val enabled: Boolean,
    val minimumAgeHours: Long,
    val cleanupIntervalMinutes: Long,
    val maxDeletesPerRun: Int
) {
    companion object {
        fun from(policy: DataFileOrphanCleanupPolicy) = DataFileOrphanCleanupPolicyResponse(
            enabled = policy.enabled,
            minimumAgeHours = policy.minimumAge.toHours(),
            cleanupIntervalMinutes = policy.cleanupInterval.toMinutes(),
            maxDeletesPerRun = policy.maxDeletesPerRun
        )
    }
}

@Serializable
data class DataFileOrphanCleanupResultResponse(
    val cutoff: String,
    val scannedFiles: Int,
    val orphanCandidates: Int,
    val deletedFiles: Int,
    val deletedBytes: Long,
    val failures: Int,
    val completedAt: String
) {
    companion object {
        fun from(result: DataFileOrphanCleanupResult) = DataFileOrphanCleanupResultResponse(
            cutoff = result.cutoff.toString(),
            scannedFiles = result.scannedFiles,
            orphanCandidates = result.orphanCandidates,
            deletedFiles = result.deletedFiles,
            deletedBytes = result.deletedBytes,
            failures = result.failures,
            completedAt = result.completedAt.toString()
        )
    }
}

@Serializable
data class DataFileOrphanCleanupStateResponse(
    val running: Boolean,
    val lastStartedAt: String?,
    val lastResult: DataFileOrphanCleanupResultResponse?,
    val lastFailure: String?
) {
    companion object {
        fun from(state: DataFileOrphanCleanupManagerState) = DataFileOrphanCleanupStateResponse(
            running = state.running,
            lastStartedAt = state.lastStartedAt?.toString(),
            lastResult = state.lastResult?.let(DataFileOrphanCleanupResultResponse::from),
            lastFailure = state.lastFailure
        )
    }
}

@Serializable
data class DataFileOrphanCleanupOverviewResponse(
    val policy: DataFileOrphanCleanupPolicyResponse,
    val state: DataFileOrphanCleanupStateResponse
)

@Serializable
data class DataFileOrphanCleanupEstimateResponse(
    val cutoff: String,
    val scannedFiles: Int,
    val orphanFiles: Int,
    val orphanBytes: Long,
    val collectedAt: String
) {
    companion object {
        fun from(estimate: DataFileOrphanCleanupEstimate) = DataFileOrphanCleanupEstimateResponse(
            cutoff = estimate.cutoff.toString(),
            scannedFiles = estimate.scannedFiles,
            orphanFiles = estimate.orphanFiles,
            orphanBytes = estimate.orphanBytes,
            collectedAt = estimate.collectedAt.toString()
        )
    }
}
