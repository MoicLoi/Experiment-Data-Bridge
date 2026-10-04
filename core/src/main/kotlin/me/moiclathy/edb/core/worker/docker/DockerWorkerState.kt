package me.moiclathy.edb.core.worker.docker

import java.net.URI
import java.time.Instant
import java.time.Duration

enum class DockerWorkerStatus {
    ABSENT,
    CREATED,
    STARTING,
    RUNNING,
    HEALTHY,
    UNHEALTHY,
    STOPPING,
    STOPPED,
    FAILED
}

data class DockerWorkerState(
    val status: DockerWorkerStatus,
    val containerName: String,
    val endpoint: URI,
    val containerId: String? = null,
    val healthy: Boolean = false,
    val detail: String? = null,
    val observedAt: Instant = Instant.now()
)

data class DockerWorkerHealth(
    val healthy: Boolean,
    val detail: String? = null
)

data class DockerWorkerLogOptions(
    val tailLines: Int = 200,
    val includeStdout: Boolean = true,
    val includeStderr: Boolean = true,
    val includeTimestamps: Boolean = true,
    val timeout: Duration = Duration.ofSeconds(5)
) {
    init {
        require(tailLines in 1..MAX_TAIL_LINES) { "Docker worker log tail must be between 1 and $MAX_TAIL_LINES lines." }
        require(includeStdout || includeStderr) { "At least one Docker worker log stream must be selected." }
        require(timeout.isPositive) { "Docker worker log timeout must be positive." }
    }

    companion object {
        const val MAX_TAIL_LINES = 10_000
    }
}

data class DockerWorkerLogs(
    val containerId: String,
    val content: String,
    val collectedAt: Instant = Instant.now()
)

data class DockerWorkerContainerSnapshot(
    val id: String,
    val name: String,
    val image: String,
    val labels: Map<String, String>,
    val running: Boolean,
    val publishedPorts: Set<DockerWorkerPublishedPort> = emptySet(),
    val exitCode: Long? = null,
    val error: String? = null
)

data class DockerWorkerPublishedPort(
    val bindAddress: String,
    val hostPort: Int,
    val containerPort: Int
)
