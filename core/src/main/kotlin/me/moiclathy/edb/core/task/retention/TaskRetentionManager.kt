package me.moiclathy.edb.core.task.retention

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Runs retention immediately at startup and periodically afterwards when enabled. */
class TaskRetentionManager(
    private val service: TaskRetentionService,
    private val policy: TaskRetentionPolicy,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC()
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private var job: Job? = null
    private val cleanupMutex = Mutex()
    private val mutableState = MutableStateFlow(TaskRetentionManagerState())
    val state: StateFlow<TaskRetentionManagerState> = mutableState.asStateFlow()

    fun start(): Boolean = synchronized(this) {
        if (!policy.enabled || !started.compareAndSet(false, true)) return false
        val supervisor = SupervisorJob()
        job = CoroutineScope(supervisor + dispatcher).launch {
            while (currentCoroutineContext().isActive) {
                try {
                    val result = runNow()
                    if (result.deletedTasks > 0) {
                        logger.info(
                            "Deleted {} expired tasks in {} batch(es), cutoff={}",
                            result.deletedTasks,
                            result.completedBatches,
                            result.cutoff
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    logger.error("Task retention cleanup failed", failure)
                }
                delay(policy.cleanupInterval.toMillis())
            }
        }
        true
    }

    suspend fun runNow(): TaskCleanupResult {
        if (!cleanupMutex.tryLock()) throw TaskRetentionAlreadyRunningException()
        mutableState.value = mutableState.value.copy(
            running = true,
            lastStartedAt = Instant.now(clock),
            lastFailure = null
        )
        return try {
            val result = withContext(dispatcher) { service.cleanup() }
            mutableState.value = mutableState.value.copy(
                running = false,
                lastResult = result,
                lastFailure = null
            )
            result
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(running = false)
            throw cancelled
        } catch (failure: Exception) {
            mutableState.value = mutableState.value.copy(
                running = false,
                lastFailure = failure.message ?: failure::class.simpleName
            )
            throw failure
        } finally {
            cleanupMutex.unlock()
        }
    }

    override fun close() {
        val running = synchronized(this) {
            if (!started.compareAndSet(true, false)) return
            job.also { job = null }
        }
        runBlocking { running?.cancelAndJoin() }
    }

    companion object {
        private val logger = LogManager.getLogger(TaskRetentionManager::class.java)
    }
}

data class TaskRetentionManagerState(
    val running: Boolean = false,
    val lastStartedAt: Instant? = null,
    val lastResult: TaskCleanupResult? = null,
    val lastFailure: String? = null
)
