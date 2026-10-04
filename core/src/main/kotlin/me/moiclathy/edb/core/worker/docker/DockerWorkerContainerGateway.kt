package me.moiclathy.edb.core.worker.docker

interface DockerWorkerContainerGateway {
    fun pullImage(image: String, credential: DockerRegistryCredential? = null)
    fun findByName(name: String): DockerWorkerContainerSnapshot?
    fun create(config: DockerWorkerRuntimeConfig): String
    fun inspect(containerId: String): DockerWorkerContainerSnapshot?
    fun findRunningByPublishedPort(bindAddress: String, hostPort: Int): DockerWorkerContainerSnapshot? = null
    fun start(containerId: String)
    fun stop(containerId: String, timeoutSeconds: Int)
    fun logs(containerId: String, options: DockerWorkerLogOptions): String
    fun rename(containerId: String, name: String)
    fun remove(containerId: String, force: Boolean = false)
}

data class DockerRegistryCredential(val registryAddress: String, val username: String, val password: String)

enum class DockerWorkerFailureCategory {
    CONFIGURATION,
    CONNECTION,
    AUTHORIZATION,
    CONFLICT,
    NOT_FOUND,
    ENGINE,
    HEALTH,
    TIMEOUT,
    UNKNOWN
}

open class DockerWorkerException(
    val category: DockerWorkerFailureCategory,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class DockerWorkerOwnershipException(message: String) : DockerWorkerException(
    DockerWorkerFailureCategory.CONFLICT,
    false,
    message
)

class DockerWorkerConfigurationException(message: String) : DockerWorkerException(
    DockerWorkerFailureCategory.CONFIGURATION,
    false,
    message
)

class DockerWorkerReadinessException(
    message: String,
    timedOut: Boolean = false
) : DockerWorkerException(
    if (timedOut) DockerWorkerFailureCategory.TIMEOUT else DockerWorkerFailureCategory.HEALTH,
    true,
    message
)

class DockerWorkerLogTimeoutException(message: String) : DockerWorkerException(
    DockerWorkerFailureCategory.TIMEOUT,
    true,
    message
)

class DockerWorkerPortConflictException(message: String) : DockerWorkerException(
    DockerWorkerFailureCategory.CONFLICT,
    false,
    message
)

class DockerWorkerOperationException(
    category: DockerWorkerFailureCategory,
    retryable: Boolean,
    operation: String,
    cause: Throwable
) : DockerWorkerException(
    category,
    retryable,
    "Docker worker operation '$operation' failed: ${cause.message ?: cause::class.simpleName}",
    cause
)
