package me.moiclathy.edb.core.datafile.retention

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import me.moiclathy.edb.core.exception.EDBException
import org.apache.logging.log4j.LogManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

data class DataFileRetentionPolicy(
    val enabled: Boolean = true,
    val retentionPeriod: Duration = Duration.ofDays(90),
    val cleanupInterval: Duration = Duration.ofDays(1),
    val batchSize: Int = 500,
    val maxBatchesPerRun: Int = 10
) {
    init {
        require(!retentionPeriod.isNegative && !retentionPeriod.isZero) { "DataFile retention period must be positive." }
        require(!cleanupInterval.isNegative && !cleanupInterval.isZero) { "DataFile retention interval must be positive." }
        require(batchSize in 1..10_000) { "DataFile retention batch size must be between 1 and 10000." }
        require(maxBatchesPerRun in 1..1_000) { "DataFile retention max batches must be between 1 and 1000." }
    }
}

data class DataFileRetentionEstimate(
    val cutoff: Instant,
    val expiredDeletedFiles: Long,
    val collectedAt: Instant
)

data class DataFileRetentionResult(
    val cutoff: Instant,
    val deletedRecords: Int,
    val completedBatches: Int,
    val completedAt: Instant
)

class DataFileRetentionService(
    private val repository: DataFileRetentionRepository,
    private val policy: DataFileRetentionPolicy,
    private val clock: Clock = Clock.systemUTC()
) {
    fun estimate(): DataFileRetentionEstimate {
        val collectedAt = Instant.now(clock)
        val cutoff = collectedAt.minus(policy.retentionPeriod)
        return DataFileRetentionEstimate(cutoff, repository.countExpiredDeleted(cutoff), collectedAt)
    }

    fun cleanup(): DataFileRetentionResult {
        val cutoff = Instant.now(clock).minus(policy.retentionPeriod)
        var deleted = 0
        var batches = 0
        while (batches < policy.maxBatchesPerRun) {
            val batch = repository.deleteExpiredDeleted(cutoff, policy.batchSize)
            if (batch == 0) break
            deleted += batch
            batches++
            if (batch < policy.batchSize) break
        }
        return DataFileRetentionResult(cutoff, deleted, batches, Instant.now(clock))
    }
}

class DataFileRetentionAlreadyRunningException : EDBException(
    code = "DATA_FILE_RETENTION_ALREADY_RUNNING",
    message = "DataFile retention cleanup is already running"
)

data class DataFileRetentionManagerState(
    val running: Boolean = false,
    val lastStartedAt: Instant? = null,
    val lastResult: DataFileRetentionResult? = null,
    val lastFailure: String? = null
)

class DataFileRetentionManager(
    private val service: DataFileRetentionService,
    private val policy: DataFileRetentionPolicy,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC()
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val runMutex = Mutex()
    private val mutableState = MutableStateFlow(DataFileRetentionManagerState())
    val state: StateFlow<DataFileRetentionManagerState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun start(): Boolean = synchronized(this) {
        if (!policy.enabled || !started.compareAndSet(false, true)) return false
        val supervisor = SupervisorJob()
        job = CoroutineScope(supervisor + dispatcher).launch {
            while (currentCoroutineContext().isActive) {
                try {
                    val result = runNow()
                    if (result.deletedRecords > 0) {
                        logger.info("Deleted {} expired DataFile record(s)", result.deletedRecords)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    logger.error("DataFile retention cleanup failed", failure)
                }
                delay(policy.cleanupInterval.toMillis())
            }
        }
        true
    }

    suspend fun runNow(): DataFileRetentionResult {
        if (!runMutex.tryLock()) throw DataFileRetentionAlreadyRunningException()
        mutableState.value = mutableState.value.copy(
            running = true,
            lastStartedAt = Instant.now(clock),
            lastFailure = null
        )
        return try {
            val result = withContext(dispatcher) { service.cleanup() }
            mutableState.value = mutableState.value.copy(running = false, lastResult = result)
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
            runMutex.unlock()
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
        private val logger = LogManager.getLogger(DataFileRetentionManager::class.java)
    }
}

class DataFileRetentionAdminService(
    val policy: DataFileRetentionPolicy,
    private val service: DataFileRetentionService,
    private val manager: DataFileRetentionManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun state(): DataFileRetentionManagerState = manager.state.value
    suspend fun estimate(): DataFileRetentionEstimate = withContext(dispatcher) { service.estimate() }
    suspend fun cleanup(): DataFileRetentionResult = manager.runNow()
}
