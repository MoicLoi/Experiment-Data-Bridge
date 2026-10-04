package me.moiclathy.edb.core.worker.docker

import java.net.URI
import java.time.Duration

data class DockerWorkerRuntimeConfig(
    val image: String,
    val workerKind: String = "python-http",
    val containerName: String = "edb-python-worker",
    val host: String = "127.0.0.1",
    val bindAddress: String = "127.0.0.1",
    val hostPort: Int = 8000,
    val containerPort: Int = 8000,
    val healthPath: String = "/health",
    val bearerToken: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val maxUploadBytes: Long = 10L * 1024 * 1024 * 1024,
    val cpuLimit: Double = 2.0,
    val memoryLimitBytes: Long = 4L * 1024 * 1024 * 1024,
    val temporaryStorageBytes: Long = 2L * 1024 * 1024 * 1024,
    val healthRequestTimeout: Duration = Duration.ofSeconds(3),
    val readinessTimeout: Duration = Duration.ofSeconds(60),
    val readinessPollInterval: Duration = Duration.ofMillis(500),
    val stopTimeout: Duration = Duration.ofSeconds(20)
) {
    init {
        require(image.isNotBlank()) { "Docker worker image must not be blank." }
        require(workerKind.matches(WORKER_KIND)) { "Docker worker kind is invalid." }
        require(containerName.matches(CONTAINER_NAME)) { "Docker worker container name is invalid." }
        require(host.isNotBlank()) { "Docker worker host must not be blank." }
        require(bindAddress.matches(IPV4_ADDRESS)) { "Docker worker bind address must be an IPv4 address." }
        require(hostPort in 1..65535 && containerPort in 1..65535) { "Docker worker ports must be valid." }
        require(healthPath.startsWith('/') && !healthPath.startsWith("//")) { "Health path must be absolute." }
        require(bearerToken == null || bearerToken.isNotBlank()) { "Docker worker bearer token must not be blank." }
        require(environment.keys.all { it.matches(ENVIRONMENT_NAME) }) { "Docker worker environment variable name is invalid." }
        require(environment.values.none { '\u0000' in it }) { "Docker worker environment values must not contain NUL." }
        require(environment.keys.none { it in RESERVED_ENVIRONMENT }) { "Docker worker environment contains a reserved variable." }
        require(labels.keys.all { it.isNotBlank() } && labels.values.none { '\u0000' in it }) { "Docker worker labels are invalid." }
        require(labels.keys.none { it in RESERVED_LABELS }) { "Docker worker labels contain a reserved label." }
        require(maxUploadBytes > 0 && memoryLimitBytes > 0 && temporaryStorageBytes > 0) { "Docker worker size limits must be positive." }
        require(cpuLimit > 0.0 && cpuLimit.isFinite()) { "Docker worker CPU limit must be positive and finite." }
        require(healthRequestTimeout.isPositive()) { "Health request timeout must be positive." }
        require(readinessTimeout.isPositive()) { "Readiness timeout must be positive." }
        require(readinessPollInterval.isPositive()) { "Readiness poll interval must be positive." }
        require(stopTimeout.isPositive()) { "Stop timeout must be positive." }
    }

    val endpoint: URI get() = URI.create("http://$host:$hostPort")
    val healthEndpoint: URI get() = endpoint.resolve(healthPath)

    companion object {
        private val CONTAINER_NAME = Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]*")
        private val WORKER_KIND = Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,99}")
        private val ENVIRONMENT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val IPV4_ADDRESS = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
        private val RESERVED_ENVIRONMENT = setOf(
            "EDB_WORKER_HOST",
            "EDB_WORKER_PORT",
            "EDB_WORKER_MAX_UPLOAD_BYTES",
            "EDB_WORKER_BEARER_TOKEN"
        )
        private val RESERVED_LABELS = setOf(
            DockerJavaWorkerContainerGateway.MANAGED_LABEL,
            DockerJavaWorkerContainerGateway.WORKER_KIND_LABEL
        )
    }
}
