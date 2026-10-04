package me.moiclathy.edb.core.task.retry

import java.time.Duration
import kotlin.math.pow

data class RetryPolicy(
    val maxAttempts: Int = 1,
    val initialDelay: Duration = Duration.ofSeconds(1),
    val backoffMultiplier: Double = 2.0,
    val maxDelay: Duration = Duration.ofMinutes(1)
) {
    init {
        require(maxAttempts >= 1) { "Maximum attempts must be at least one." }
        require(!initialDelay.isNegative) { "Initial retry delay must not be negative." }
        require(backoffMultiplier >= 1.0 && backoffMultiplier.isFinite()) {
            "Retry backoff multiplier must be finite and at least one."
        }
        require(!maxDelay.isNegative && maxDelay >= initialDelay) {
            "Maximum retry delay must not be negative or shorter than the initial delay."
        }
    }

    fun canRetry(attemptCount: Int): Boolean = attemptCount < maxAttempts

    fun delayAfterFailure(attemptCount: Int): Duration {
        require(attemptCount >= 1) { "Attempt count must be at least one." }
        val scaledMillis = initialDelay.toMillis().toDouble() *
            backoffMultiplier.pow((attemptCount - 1).toDouble())
        return Duration.ofMillis(
            scaledMillis.coerceAtMost(maxDelay.toMillis().toDouble()).toLong()
        )
    }

    companion object {
        val NONE = RetryPolicy()
    }
}
