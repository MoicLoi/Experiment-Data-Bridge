package me.moiclathy.edb.core.datafile.lifecycle

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
import me.moiclathy.edb.core.datafile.repository.DataFileStorageReferenceIndex
import me.moiclathy.edb.core.datafile.storage.DataFileStorage
import me.moiclathy.edb.core.datafile.storage.DataFileStorageInventory
import me.moiclathy.edb.core.exception.EDBException
import org.apache.logging.log4j.LogManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

data class DataFileOrphanCleanupPolicy(
    val enabled: Boolean = true,
    val minimumAge: Duration = Duration.ofHours(24),
    val cleanupInterval: Duration = Duration.ofHours(6),
    val maxDeletesPerRun: Int = 1_000
) {
    init {
        require(!minimumAge.isNegative && !minimumAge.isZero) { "Orphan minimum age must be positive." }
        require(!cleanupInterval.isNegative && !cleanupInterval.isZero) { "Orphan cleanup interval must be positive." }
        require(maxDeletesPerRun in 1..100_000) { "Orphan cleanup limit must be between 1 and 100000." }
    }
}

data class DataFileOrphanCleanupResult(
    val cutoff: Instant,
    val scannedFiles: Int,
    val orphanCandidates: Int,
    val deletedFiles: Int,
    val deletedBytes: Long,
    val failures: Int,
    val completedAt: Instant
)

data class DataFileOrphanCleanupEstimate(
    val cutoff: Instant,
    val scannedFiles: Int,
    val orphanFiles: Int,
    val orphanBytes: Long,
    val collectedAt: Instant
)

class DataFileOrphanCleanupAlreadyRunningException : EDBException(
    code = "DATA_FILE_ORPHAN_CLEANUP_ALREADY_RUNNING",
    message = "DataFile orphan cleanup is already running"
)

class DataFileOrphanCleanupService(
    private val storage: DataFileStorage,
    private val inventory: DataFileStorageInventory,
    private val referenceIndex: DataFileStorageReferenceIndex,
    private val policy: DataFileOrphanCleanupPolicy,
    private val clock: Clock = Clock.systemUTC()
) {
    fun estimate(): DataFileOrphanCleanupEstimate {
        val collectedAt = Instant.now(clock)
        val snapshot = snapshot(collectedAt.minus(policy.minimumAge))
        return DataFileOrphanCleanupEstimate(
            cutoff = snapshot.cutoff,
            scannedFiles = snapshot.scannedFiles,
            orphanFiles = snapshot.candidates.size,
            orphanBytes = snapshot.candidates.sumOf { it.sizeBytes },
            collectedAt = collectedAt
        )
    }

    fun cleanup(): DataFileOrphanCleanupResult {
        val snapshot = snapshot(Instant.now(clock).minus(policy.minimumAge))
        val selected = snapshot.candidates.asSequence()
            .take(policy.maxDeletesPerRun)
            .toList()
        var deletedFiles = 0
        var deletedBytes = 0L
        var failures = 0
        selected.forEach { entry ->
            try {
                if (storage.delete(entry.key)) {
                    deletedFiles++
                    deletedBytes += entry.sizeBytes
                }
            } catch (failure: Exception) {
                failures++
                logger.warn("Failed to delete orphan DataFile storage key: {}", entry.key, failure)
            }
        }
        return DataFileOrphanCleanupResult(
            cutoff = snapshot.cutoff,
            scannedFiles = snapshot.scannedFiles,
            orphanCandidates = snapshot.candidates.size,
            deletedFiles = deletedFiles,
            deletedBytes = deletedBytes,
            failures = failures,
            completedAt = Instant.now(clock)
        )
    }

    private fun snapshot(cutoff: Instant): CleanupSnapshot {
        val retained = referenceIndex.findRetainedStorageKeys(inventory.provider)
        val entries = inventory.listCommitted()
        return CleanupSnapshot(
            cutoff = cutoff,
            scannedFiles = entries.size,
            candidates = entries
                .filter { it.key !in retained && !it.lastModifiedAt.isAfter(cutoff) }
                .sortedBy { it.lastModifiedAt }
        )
    }

    private data class CleanupSnapshot(
        val cutoff: Instant,
        val scannedFiles: Int,
        val candidates: List<me.moiclathy.edb.core.datafile.storage.DataFileStorageEntry>
    )

    companion object {
        private val logger = LogManager.getLogger(DataFileOrphanCleanupService::class.java)
    }
}

/** Executes one reconciliation at startup and repeats it until application shutdown. */
class DataFileOrphanCleanupManager(
    private val service: DataFileOrphanCleanupService,
    private val policy: DataFileOrphanCleanupPolicy,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC()
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val runMutex = Mutex()
    private val mutableState = MutableStateFlow(DataFileOrphanCleanupManagerState())
    val state: StateFlow<DataFileOrphanCleanupManagerState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun start(): Boolean = synchronized(this) {
        if (!policy.enabled || !started.compareAndSet(false, true)) return false
        val supervisor = SupervisorJob()
        job = CoroutineScope(supervisor + dispatcher).launch {
            while (currentCoroutineContext().isActive) {
                try {
                    val result = runNow()
                    if (result.deletedFiles > 0 || result.failures > 0) {
                        logger.info(
                            "DataFile orphan cleanup deleted {} file(s) and {} byte(s); failures={}",
                            result.deletedFiles,
                            result.deletedBytes,
                            result.failures
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    logger.error("DataFile orphan cleanup failed", failure)
                }
                delay(policy.cleanupInterval.toMillis())
            }
        }
        true
    }

    suspend fun runNow(): DataFileOrphanCleanupResult {
        if (!runMutex.tryLock()) throw DataFileOrphanCleanupAlreadyRunningException()
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
        private val logger = LogManager.getLogger(DataFileOrphanCleanupManager::class.java)
    }
}

data class DataFileOrphanCleanupManagerState(
    val running: Boolean = false,
    val lastStartedAt: Instant? = null,
    val lastResult: DataFileOrphanCleanupResult? = null,
    val lastFailure: String? = null
)
