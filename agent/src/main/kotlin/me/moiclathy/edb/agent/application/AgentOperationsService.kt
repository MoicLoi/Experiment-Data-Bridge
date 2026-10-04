package me.moiclathy.edb.agent.application

import me.moiclathy.edb.agent.persistence.AgentOperationsRepository
import me.moiclathy.edb.agent.persistence.AgentRunMetrics
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

data class AgentMetricsSnapshot(val metrics: AgentRunMetrics, val collectedAt: Instant)
data class AgentRetentionPolicy(
    val retentionPeriod: Duration = Duration.ofDays(90),
    val batchSize: Int = 100,
    val maxBatches: Int = 10,
    val enabled: Boolean = false,
    val cleanupInterval: Duration = Duration.ofHours(1),
    val initialDelay: Duration = Duration.ofMinutes(5)
) {
    init {
        require(!retentionPeriod.isNegative && !retentionPeriod.isZero)
        require(batchSize > 0 && maxBatches > 0)
        require(!cleanupInterval.isNegative && !cleanupInterval.isZero)
        require(!initialDelay.isNegative)
    }
}
data class AgentRetentionEstimate(val cutoff: Instant, val expiredRuns: Long, val collectedAt: Instant)
data class AgentRetentionCleanupResult(val cutoff: Instant, val deletedRuns: Int, val batches: Int, val completedAt: Instant)

class AgentOperationsService(
    private val repository: AgentOperationsRepository,
    val retentionPolicy: AgentRetentionPolicy = AgentRetentionPolicy(),
    private val clock: Clock = Clock.systemUTC()
) {
    private val lastCleanup = AtomicReference<AgentRetentionCleanupResult?>(null)

    fun metrics() = AgentMetricsSnapshot(repository.metrics(), Instant.now(clock))

    fun estimate(): AgentRetentionEstimate {
        val now = Instant.now(clock)
        val cutoff = now.minus(retentionPolicy.retentionPeriod)
        return AgentRetentionEstimate(cutoff, repository.countExpiredRuns(cutoff), now)
    }

    fun cleanup(): AgentRetentionCleanupResult {
        val cutoff = Instant.now(clock).minus(retentionPolicy.retentionPeriod)
        var deleted = 0
        var batches = 0
        while (batches < retentionPolicy.maxBatches) {
            val count = repository.deleteExpiredRuns(cutoff, retentionPolicy.batchSize)
            if (count == 0) break
            deleted += count
            batches++
            if (count < retentionPolicy.batchSize) break
        }
        return AgentRetentionCleanupResult(cutoff, deleted, batches, Instant.now(clock)).also(lastCleanup::set)
    }

    fun lastResult(): AgentRetentionCleanupResult? = lastCleanup.get()
}
