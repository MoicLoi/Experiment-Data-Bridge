package me.moiclathy.edb.core.task.scheduler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.moiclathy.edb.core.task.worker.TaskWorker
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

/** Owns and supervises a dynamically sized pool of [TaskWorker] coroutines. */
class CoroutineTaskScheduler(
    private val worker: TaskWorker,
    private val config: TaskSchedulerConfig = TaskSchedulerConfig(),
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : TaskScheduler {
    private val lock = Any()
    private val workers = mutableMapOf<Long, WorkerSlot>()
    private var nextWorkerId = 0L
    private var desiredWorkerCount = config.workerCount
    private var supervisorJob: Job? = null
    private var scope: CoroutineScope? = null

    private val mutableState = MutableStateFlow(
        TaskSchedulerState(
            status = TaskSchedulerStatus.STOPPED,
            desiredWorkerCount = config.workerCount,
            activeWorkerCount = 0
        )
    )
    override val state: StateFlow<TaskSchedulerState> = mutableState.asStateFlow()

    override fun start(): Boolean = synchronized(lock) {
        if (mutableState.value.status != TaskSchedulerStatus.STOPPED) {
            return@synchronized false
        }

        mutableState.value = mutableState.value.copy(
            status = TaskSchedulerStatus.STARTING,
            activeWorkerCount = 0,
            startedAt = clock.instant(),
            stoppedAt = null,
            lastFailure = null
        )

        supervisorJob = SupervisorJob()
        scope = CoroutineScope(supervisorJob!! + dispatcher)
        addWorkersLocked(desiredWorkerCount)
        mutableState.value = mutableState.value.copy(
            status = TaskSchedulerStatus.RUNNING,
            activeWorkerCount = workers.size
        )
        true
    }

    override fun updateWorkerCount(workerCount: Int) {
        require(workerCount > 0) { "Worker count must be greater than zero." }

        synchronized(lock) {
            desiredWorkerCount = workerCount
            mutableState.value = mutableState.value.copy(desiredWorkerCount = workerCount)

            when (mutableState.value.status) {
                TaskSchedulerStatus.RUNNING,
                TaskSchedulerStatus.DEGRADED -> resizeWorkersLocked(workerCount)

                TaskSchedulerStatus.STOPPED,
                TaskSchedulerStatus.STARTING,
                TaskSchedulerStatus.STOPPING -> Unit
            }
        }
    }

    override suspend fun stop(): Boolean {
        val shutdown: ShutdownSnapshot = synchronized(lock) {
            when (mutableState.value.status) {
                TaskSchedulerStatus.STOPPED,
                TaskSchedulerStatus.STOPPING -> return false

                else -> Unit
            }

            mutableState.value = mutableState.value.copy(status = TaskSchedulerStatus.STOPPING)
            workers.values.forEach { it.accepting.set(false) }
            ShutdownSnapshot(workers.values.map { it.job }, supervisorJob)
        }

        val completedGracefully = withTimeoutOrNull(config.shutdownTimeout.toMillis()) {
            shutdown.jobs.joinAll()
            true
        } ?: false

        if (!completedGracefully) {
            shutdown.jobs.forEach { it.cancel(CancellationException("Task scheduler shutdown timed out.")) }
            shutdown.jobs.joinAll()
        }
        shutdown.supervisorJob?.cancel()

        synchronized(lock) {
            workers.clear()
            scope = null
            supervisorJob = null
            mutableState.value = mutableState.value.copy(
                status = TaskSchedulerStatus.STOPPED,
                activeWorkerCount = 0,
                stoppedAt = clock.instant()
            )
        }
        return true
    }

    override fun close() {
        runBlocking { stop() }
    }

    private fun resizeWorkersLocked(target: Int) {
        val acceptingWorkers = workers.values.count { it.accepting.get() }
        when {
            acceptingWorkers < target -> addWorkersLocked(target - acceptingWorkers)
            acceptingWorkers > target -> workers.values
                .asSequence()
                .filter { it.accepting.get() }
                .sortedByDescending { it.id }
                .take(acceptingWorkers - target)
                .forEach { it.accepting.set(false) }
        }

        if (workers.values.count { it.accepting.get() } == target) {
            mutableState.value = mutableState.value.copy(
                status = TaskSchedulerStatus.RUNNING,
                activeWorkerCount = workers.size,
                lastFailure = null
            )
        }
    }

    private fun addWorkersLocked(count: Int) {
        val workerScope = checkNotNull(scope) { "Scheduler scope is not initialized." }
        repeat(count) {
            val id = ++nextWorkerId
            val accepting = AtomicBoolean(true)
            val job = workerScope.launch(start = CoroutineStart.LAZY) {
                worker.runWhile { accepting.get() }
            }
            val slot = WorkerSlot(id, accepting, job)
            workers[id] = slot
            job.invokeOnCompletion { failure -> onWorkerCompleted(slot, failure) }
            job.start()
        }
        mutableState.value = mutableState.value.copy(activeWorkerCount = workers.size)
    }

    private fun onWorkerCompleted(slot: WorkerSlot, failure: Throwable?) {
        synchronized(lock) {
            if (workers.remove(slot.id) == null) return

            val current = mutableState.value
            if (current.status != TaskSchedulerStatus.STOPPING &&
                current.status != TaskSchedulerStatus.STOPPED &&
                slot.accepting.get()
            ) {
                mutableState.value = current.copy(
                    status = TaskSchedulerStatus.DEGRADED,
                    activeWorkerCount = workers.size,
                    lastFailure = failure?.message ?: "Worker stopped unexpectedly."
                )
            } else {
                mutableState.value = current.copy(activeWorkerCount = workers.size)
            }
        }
    }

    private data class WorkerSlot(
        val id: Long,
        val accepting: AtomicBoolean,
        val job: Job
    )

    private data class ShutdownSnapshot(
        val jobs: List<Job>,
        val supervisorJob: Job?
    )
}
