package me.moiclathy.edb.agent.application

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
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

data class AgentRetentionManagerState(
    val started: Boolean = false,
    val running: Boolean = false,
    val lastStartedAt: Instant? = null,
    val lastResult: AgentRetentionCleanupResult? = null,
    val lastFailure: String? = null
)

class AgentRetentionManager(
    private val service: AgentOperationsService,
    private val policy: AgentRetentionPolicy,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC()
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val cleanupMutex = Mutex()
    private val mutableState = MutableStateFlow(AgentRetentionManagerState())
    private var job: Job? = null
    val state: StateFlow<AgentRetentionManagerState> = mutableState.asStateFlow()

    fun start(): Boolean = synchronized(this) {
        if (!policy.enabled || !started.compareAndSet(false, true)) return false
        mutableState.value = mutableState.value.copy(started = true)
        job = CoroutineScope(SupervisorJob() + dispatcher).launch {
            if (!policy.initialDelay.isZero) delay(policy.initialDelay.toMillis())
            while (currentCoroutineContext().isActive) {
                try {
                    runNow()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Failure details are exposed through state; the next interval is still scheduled.
                }
                delay(policy.cleanupInterval.toMillis())
            }
        }
        true
    }

    suspend fun runNow(): AgentRetentionCleanupResult {
        if (!cleanupMutex.tryLock()) throw AgentRetentionAlreadyRunningException()
        mutableState.value = mutableState.value.copy(running = true, lastStartedAt = Instant.now(clock), lastFailure = null)
        return try {
            withContext(dispatcher) { service.cleanup() }.also { result ->
                mutableState.value = mutableState.value.copy(running = false, lastResult = result)
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(running = false)
            throw cancelled
        } catch (failure: Exception) {
            mutableState.value = mutableState.value.copy(
                running = false, lastFailure = failure.message ?: failure::class.simpleName
            )
            throw failure
        } finally {
            cleanupMutex.unlock()
        }
    }

    override fun close() {
        val runningJob = synchronized(this) {
            if (!started.compareAndSet(true, false)) return
            mutableState.value = mutableState.value.copy(started = false)
            job.also { job = null }
        }
        runBlocking { runningJob?.cancelAndJoin() }
    }
}

class AgentRetentionAlreadyRunningException :
    IllegalStateException("Agent retention cleanup is already running.")
