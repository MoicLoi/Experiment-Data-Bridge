package me.moiclathy.edb.core.task.worker

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.moiclathy.edb.core.task.runtime.TaskRunner
import java.time.Duration

/**
 * Pull-based worker whose lifecycle is owned by the calling coroutine scope.
 */
class TaskWorker(
    private val runner: TaskRunner,
    private val pollInterval: Duration = Duration.ofSeconds(1)
) {
    init {
        require(!pollInterval.isNegative && !pollInterval.isZero) {
            "Worker poll interval must be greater than zero."
        }
    }

    /** Claims and executes at most one task. Returns false when the queue is empty. */
    suspend fun runOnce(): Boolean = runner.runOnce()

    /**
     * Runs until the coroutine is cancelled or the caller asks this worker to stop.
     * The condition is checked between tasks so an in-flight task can finish cleanly.
     */
    suspend fun runWhile(shouldContinue: () -> Boolean) {
        while (currentCoroutineContext().isActive && shouldContinue()) {
            if (!runOnce()) {
                if (shouldContinue()) {
                    delay(pollInterval.toMillis())
                }
            }
        }
    }

    suspend fun runUntilCancelled() = runWhile { true }
}
