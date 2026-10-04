package me.moiclathy.edb.core.task.retention

import java.time.Duration

data class TaskRetentionPolicy(
    val enabled: Boolean = false,
    val retentionPeriod: Duration = Duration.ofDays(90),
    val cleanupInterval: Duration = Duration.ofHours(1),
    val batchSize: Int = 500,
    val maxBatchesPerRun: Int = 10
) {
    init {
        require(!retentionPeriod.isNegative && !retentionPeriod.isZero) {
            "Task retention period must be greater than zero."
        }
        require(!cleanupInterval.isNegative && !cleanupInterval.isZero) {
            "Task cleanup interval must be greater than zero."
        }
        require(batchSize in 1..MAX_BATCH_SIZE) {
            "Task cleanup batch size must be between 1 and $MAX_BATCH_SIZE."
        }
        require(maxBatchesPerRun in 1..MAX_BATCHES_PER_RUN) {
            "Task cleanup max batches must be between 1 and $MAX_BATCHES_PER_RUN."
        }
    }

    companion object {
        const val MAX_BATCH_SIZE = 5_000
        const val MAX_BATCHES_PER_RUN = 100
    }
}
