package me.moiclathy.edb.core.worker.docker

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import java.time.Duration

class DockerWorkerRuntimeFactory(
    private val connectionTimeout: Duration = Duration.ofSeconds(5),
    private val responseTimeout: Duration = Duration.ofSeconds(30),
    private val maxConnections: Int = 20
) {
    init {
        require(connectionTimeout.isPositive) { "Docker connection timeout must be positive." }
        require(responseTimeout.isPositive) { "Docker response timeout must be positive." }
        require(maxConnections > 0) { "Docker max connections must be positive." }
    }

    fun create(config: DockerWorkerRuntimeConfig): DockerWorkerLifecycle {
        val (dockerClient, _) = client()
        return try {
            ManagedDockerWorkerLifecycle(DockerWorkerRuntime(config, DockerJavaWorkerContainerGateway(dockerClient)), dockerClient)
        } catch (failure: Exception) { dockerClient.close(); throw failure }
    }

    fun createRebuildSession(): DockerWorkerRebuildSession {
        val (dockerClient, gateway) = client()
        return DockerWorkerRebuildSession(DockerWorkerRebuildService(gateway), gateway, dockerClient::close)
    }

    private fun client(): Pair<com.github.dockerjava.api.DockerClient, DockerJavaWorkerContainerGateway> {
        val clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(clientConfig.dockerHost)
            .sslConfig(clientConfig.sslConfig)
            .maxConnections(maxConnections)
            .connectionTimeout(connectionTimeout)
            .responseTimeout(responseTimeout)
            .build()
        val dockerClient = DockerClientImpl.getInstance(clientConfig, httpClient)
        return dockerClient to DockerJavaWorkerContainerGateway(dockerClient)
    }
}

class DockerWorkerRebuildSession(val service: DockerWorkerRebuildService, private val gateway: DockerWorkerContainerGateway, private val closeAction: () -> Unit) : AutoCloseable {
    fun findByName(name: String) = gateway.findByName(name)
    override fun close() = closeAction()
}
