package me.moiclathy.edb.core.worker.docker

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import kotlin.math.ceil

class DockerWorkerRuntime(
    private val config: DockerWorkerRuntimeConfig,
    private val containers: DockerWorkerContainerGateway,
    private val healthClient: DockerWorkerHealthClient = HttpDockerWorkerHealthClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val lifecycleMutex = Mutex()
    private val mutableState = MutableStateFlow(state(DockerWorkerStatus.ABSENT))

    val state: StateFlow<DockerWorkerState> = mutableState.asStateFlow()

    suspend fun ensureCreated(): DockerWorkerState = lifecycleMutex.withLock {
        val container = findOwnedContainer() ?: run {
            val id = dockerCall { containers.create(config) }
            dockerCall { containers.inspect(id) }
                ?: throw DockerWorkerConfigurationException("Docker created worker container $id but it cannot be inspected.")
        }
        validateConfiguration(container)
        publish(snapshotState(container))
    }

    suspend fun start(): DockerWorkerState = lifecycleMutex.withLock { startLocked() }

    suspend fun stop(): DockerWorkerState = lifecycleMutex.withLock {
        val container = findOwnedContainer()
            ?: return@withLock publish(state(DockerWorkerStatus.ABSENT))
        validateConfiguration(container)
        if (!container.running) return@withLock publish(snapshotState(container))
        publish(state(DockerWorkerStatus.STOPPING, container.id))
        dockerCall {
            containers.stop(container.id, ceil(config.stopTimeout.toMillis() / 1_000.0).toInt().coerceAtLeast(1))
        }
        val stopped = dockerCall { containers.inspect(container.id) }
        publish(stopped?.let(::snapshotState) ?: state(DockerWorkerStatus.ABSENT))
    }

    suspend fun restart(): DockerWorkerState = lifecycleMutex.withLock {
        val container = findOwnedContainer()
        if (container?.running == true) {
            publish(state(DockerWorkerStatus.STOPPING, container.id))
            dockerCall {
                containers.stop(container.id, ceil(config.stopTimeout.toMillis() / 1_000.0).toInt().coerceAtLeast(1))
            }
        }
        startLocked()
    }

    suspend fun status(): DockerWorkerState {
        val container = findOwnedContainer()
            ?: return publish(state(DockerWorkerStatus.ABSENT))
        validateConfiguration(container)
        if (!container.running) return publish(snapshotState(container))
        val health = healthClient.check(config.healthEndpoint, config.healthRequestTimeout)
        return publish(
            state(
                status = if (health.healthy) DockerWorkerStatus.HEALTHY else DockerWorkerStatus.UNHEALTHY,
                containerId = container.id,
                healthy = health.healthy,
                detail = health.detail
            )
        )
    }

    suspend fun logs(options: DockerWorkerLogOptions = DockerWorkerLogOptions()): DockerWorkerLogs {
        val container = findOwnedContainer()
            ?: throw DockerWorkerOperationException(
                DockerWorkerFailureCategory.NOT_FOUND,
                false,
                "logs",
                NoSuchElementException("Managed Docker worker container does not exist.")
            )
        validateConfiguration(container)
        val content = dockerCall { containers.logs(container.id, options) }
        return DockerWorkerLogs(container.id, content, Instant.now(clock))
    }

    private suspend fun startLocked(): DockerWorkerState {
        var container = findOwnedContainer() ?: run {
            val id = dockerCall { containers.create(config) }
            dockerCall { containers.inspect(id) }
                ?: throw DockerWorkerConfigurationException("Docker created worker container $id but it cannot be inspected.")
        }
        validateConfiguration(container)
        if (!container.running) {
            val conflict = dockerCall {
                containers.findRunningByPublishedPort(config.bindAddress, config.hostPort)
            }
            if (conflict != null && conflict.id != container.id) {
                throw DockerWorkerPortConflictException(
                    "Host port ${config.bindAddress}:${config.hostPort} is already used by container '${conflict.name}'."
                )
            }
            publish(state(DockerWorkerStatus.STARTING, container.id))
            dockerCall { containers.start(container.id) }
        }
        val deadline = clock.instant().plus(config.readinessTimeout)
        while (true) {
            container = dockerCall { containers.inspect(container.id) }
                ?: throw DockerWorkerReadinessException("Docker worker container disappeared while starting.")
            if (!container.running) {
                throw DockerWorkerReadinessException(
                    "Docker worker stopped before becoming healthy${container.error?.let { ": $it" }.orEmpty()}."
                )
            }
            publish(state(DockerWorkerStatus.RUNNING, container.id))
            val health = healthClient.check(config.healthEndpoint, config.healthRequestTimeout)
            if (health.healthy) {
                return publish(state(DockerWorkerStatus.HEALTHY, container.id, healthy = true))
            }
            if (!clock.instant().isBefore(deadline)) {
                val failed = state(
                    DockerWorkerStatus.UNHEALTHY,
                    container.id,
                    detail = health.detail ?: "Worker readiness timeout expired."
                )
                publish(failed)
                throw DockerWorkerReadinessException(failed.detail!!, timedOut = true)
            }
            delay(config.readinessPollInterval.toMillis())
        }
    }

    private suspend fun findOwnedContainer(): DockerWorkerContainerSnapshot? {
        val container = dockerCall { containers.findByName(config.containerName) } ?: return null
        if (container.labels[DockerJavaWorkerContainerGateway.MANAGED_LABEL] != "true" ||
            container.labels[DockerJavaWorkerContainerGateway.WORKER_KIND_LABEL] != config.workerKind
        ) {
            throw DockerWorkerOwnershipException(
                "Container '${config.containerName}' exists but is not owned by the EDB '${config.workerKind}' runtime."
            )
        }
        return container
    }

    private fun validateConfiguration(container: DockerWorkerContainerSnapshot) {
        if (container.image != config.image) {
            throw DockerWorkerConfigurationException(
                "Managed worker container uses image '${container.image}', expected '${config.image}'."
            )
        }
    }

    private fun snapshotState(container: DockerWorkerContainerSnapshot): DockerWorkerState = state(
        status = if (container.running) DockerWorkerStatus.RUNNING else DockerWorkerStatus.STOPPED,
        containerId = container.id,
        detail = container.error ?: container.exitCode?.takeIf { it != 0L }?.let { "Container exited with code $it." }
    )

    private fun state(
        status: DockerWorkerStatus,
        containerId: String? = null,
        healthy: Boolean = false,
        detail: String? = null
    ) = DockerWorkerState(
        status = status,
        containerName = config.containerName,
        endpoint = config.endpoint,
        containerId = containerId,
        healthy = healthy,
        detail = detail,
        observedAt = Instant.now(clock)
    )

    private fun publish(value: DockerWorkerState): DockerWorkerState {
        mutableState.value = value
        return value
    }

    private suspend fun <T> dockerCall(block: () -> T): T = withContext(ioDispatcher) { block() }
}
