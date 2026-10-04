package me.moiclathy.edb.core.task.retention

import java.time.Clock
import java.time.Instant

data class TaskCleanupResult(
    val cutoff: Instant,
    val deletedTasks: Int,
    val completedBatches: Int,
    val completedAt: Instant
)

data class TaskRetentionEstimate(
    val cutoff: Instant,
    val expiredTasks: Long,
    val collectedAt: Instant
)

class TaskRetentionService(
    private val repository: TaskRetentionRepository,
    private val policy: TaskRetentionPolicy,
    private val clock: Clock = Clock.systemUTC()
) {
    fun estimate(): TaskRetentionEstimate {
        val collectedAt = Instant.now(clock)
        val cutoff = collectedAt.minus(policy.retentionPeriod)
        return TaskRetentionEstimate(
            cutoff = cutoff,
            expiredTasks = repository.countExpired(cutoff),
            collectedAt = collectedAt
        )
    }

    fun cleanup(): TaskCleanupResult {
        val cutoff = Instant.now(clock).minus(policy.retentionPeriod)
        var deletedTasks = 0
        var completedBatches = 0

        while (completedBatches < policy.maxBatchesPerRun) {
            val deleted = repository.deleteExpired(cutoff, policy.batchSize)
            if (deleted == 0) break
            deletedTasks += deleted
            completedBatches++
            if (deleted < policy.batchSize) break
        }
        return TaskCleanupResult(
            cutoff = cutoff,
            deletedTasks = deletedTasks,
            completedBatches = completedBatches,
            completedAt = Instant.now(clock)
        )
    }
}
