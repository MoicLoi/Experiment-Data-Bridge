package me.moiclathy.edb.core.worker.docker

import com.github.dockerjava.api.DockerClient
import kotlinx.coroutines.flow.StateFlow

interface DockerWorkerLifecycle : AutoCloseable {
    val state: StateFlow<DockerWorkerState>
    suspend fun ensureCreated(): DockerWorkerState
    suspend fun start(): DockerWorkerState
    suspend fun stop(): DockerWorkerState
    suspend fun restart(): DockerWorkerState
    suspend fun status(): DockerWorkerState
    suspend fun logs(options: DockerWorkerLogOptions = DockerWorkerLogOptions()): DockerWorkerLogs
}

internal class ManagedDockerWorkerLifecycle(
    private val runtime: DockerWorkerRuntime,
    private val dockerClient: DockerClient
) : DockerWorkerLifecycle {
    override val state: StateFlow<DockerWorkerState> = runtime.state

    override suspend fun ensureCreated(): DockerWorkerState = runtime.ensureCreated()

    override suspend fun start(): DockerWorkerState = runtime.start()

    override suspend fun stop(): DockerWorkerState = runtime.stop()

    override suspend fun restart(): DockerWorkerState = runtime.restart()

    override suspend fun status(): DockerWorkerState = runtime.status()

    override suspend fun logs(options: DockerWorkerLogOptions): DockerWorkerLogs = runtime.logs(options)

    override fun close() {
        dockerClient.close()
    }
}
