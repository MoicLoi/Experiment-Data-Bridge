package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetState
import me.moiclathy.edb.core.worker.docker.DockerWorkerLogs
import me.moiclathy.edb.core.worker.docker.DockerWorkerState
import me.moiclathy.edb.core.config.DockerWorkerConfig

@Serializable
data class DockerWorkerFleetResponse(
    val healthy: Boolean,
    val observedAt: String,
    val members: List<DockerWorkerResponse>
) {
    companion object {
        fun from(state: DockerWorkerFleetState) = DockerWorkerFleetResponse(
            healthy = state.healthy,
            observedAt = state.observedAt.toString(),
            members = state.workers.map { (id, worker) -> DockerWorkerResponse.from(id, worker) }
        )
    }
}

@Serializable
data class DockerWorkerResponse(
    val id: String,
    val status: String,
    val containerName: String,
    val endpoint: String,
    val containerId: String?,
    val healthy: Boolean,
    val detail: String?,
    val observedAt: String
) {
    companion object {
        fun from(id: String, state: DockerWorkerState) = DockerWorkerResponse(
            id = id,
            status = state.status.name,
            containerName = state.containerName,
            endpoint = state.endpoint.toString(),
            containerId = state.containerId,
            healthy = state.healthy,
            detail = state.detail,
            observedAt = state.observedAt.toString()
        )
    }
}

@Serializable
data class DockerWorkerLogsResponse(
    val workerId: String,
    val containerId: String,
    val content: String,
    val collectedAt: String
) {
    companion object {
        fun from(workerId: String, logs: DockerWorkerLogs) = DockerWorkerLogsResponse(
            workerId = workerId,
            containerId = logs.containerId,
            content = logs.content,
            collectedAt = logs.collectedAt.toString()
        )
    }
}

@Serializable
data class DockerImageCatalogResponse(val items: List<DockerImageCatalogItemResponse>)

@Serializable
data class DockerImageCatalogItemResponse(
    val workerId: String,
    val image: String,
    val registry: String,
    val repository: String,
    val tag: String
) {
    companion object {
        fun from(config: DockerWorkerConfig): DockerImageCatalogItemResponse {
            val reference = config.image.substringBefore('@')
            val slash = reference.lastIndexOf('/')
            val colon = reference.lastIndexOf(':')
            val tag = if (colon > slash) reference.substring(colon + 1) else "latest"
            val repositoryWithRegistry = if (colon > slash) reference.substring(0, colon) else reference
            val first = repositoryWithRegistry.substringBefore('/')
            val hasRegistry = '/' in repositoryWithRegistry && ('.' in first || ':' in first || first == "localhost")
            return DockerImageCatalogItemResponse(
                workerId = config.id,
                image = config.image,
                registry = if (hasRegistry) first else "docker.io",
                repository = if (hasRegistry) repositoryWithRegistry.substringAfter('/') else repositoryWithRegistry,
                tag = tag
            )
        }
    }
}
