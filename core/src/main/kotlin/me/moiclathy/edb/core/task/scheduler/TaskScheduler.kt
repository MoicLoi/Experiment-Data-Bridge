package me.moiclathy.edb.core.task.scheduler

import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant

enum class TaskSchedulerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    DEGRADED,
    STOPPING
}

data class TaskSchedulerConfig(
    val workerCount: Int = 1,
    val shutdownTimeout: Duration = Duration.ofSeconds(30)
) {
    init {
        require(workerCount > 0) { "Worker count must be greater than zero." }
        require(!shutdownTimeout.isNegative && !shutdownTimeout.isZero) {
            "Scheduler shutdown timeout must be greater than zero."
        }
    }
}

data class TaskSchedulerState(
    val status: TaskSchedulerStatus,
    val desiredWorkerCount: Int,
    val activeWorkerCount: Int,
    val startedAt: Instant? = null,
    val stoppedAt: Instant? = null,
    val lastFailure: String? = null
)

interface TaskScheduler : AutoCloseable {
    val state: StateFlow<TaskSchedulerState>

    /** Starts the configured worker pool. Returns false when already active. */
    fun start(): Boolean

    /** Changes the desired concurrency. Running workers are added or drained as needed. */
    fun updateWorkerCount(workerCount: Int)

    /** Stops claiming new work and waits for in-flight tasks up to the configured timeout. */
    suspend fun stop(): Boolean
}
