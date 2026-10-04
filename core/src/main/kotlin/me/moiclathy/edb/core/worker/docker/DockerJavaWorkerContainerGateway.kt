package me.moiclathy.edb.core.worker.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.AuthConfig
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class DockerJavaWorkerContainerGateway(
    private val docker: DockerClient
) : DockerWorkerContainerGateway {
    override fun pullImage(image: String, credential: DockerRegistryCredential?) {
        operation("pull-image") {
            val command = docker.pullImageCmd(image)
            credential?.let { command.withAuthConfig(AuthConfig().withRegistryAddress(it.registryAddress).withUsername(it.username).withPassword(it.password)) }
            command.exec(ResultCallback.Adapter<com.github.dockerjava.api.model.PullResponseItem>()).use { callback ->
                if (!callback.awaitCompletion(10, TimeUnit.MINUTES)) throw DockerWorkerOperationException(DockerWorkerFailureCategory.TIMEOUT, true, "pull-image", IllegalStateException("Docker image pull timed out."))
            }
        }
    }
    override fun findByName(name: String): DockerWorkerContainerSnapshot? {
        val container = operation("find") {
            docker.listContainersCmd().withShowAll(true).exec()
                .firstOrNull { candidate -> candidate.names.orEmpty().any { it == "/$name" } }
        } ?: return null
        return inspect(container.id)
    }

    override fun create(config: DockerWorkerRuntimeConfig): String {
        val exposedPort = ExposedPort.tcp(config.containerPort)
        val hostConfig = HostConfig.newHostConfig()
            .withPortBindings(
                PortBinding(Ports.Binding.bindIpAndPort(config.bindAddress, config.hostPort), exposedPort)
            )
            .withNanoCPUs((config.cpuLimit * NANO_CPUS_PER_CPU).toLong())
            .withMemory(config.memoryLimitBytes)
            .withReadonlyRootfs(true)
            .withInit(true)
            .withCapDrop(Capability.ALL)
            .withSecurityOpts(listOf("no-new-privileges:true"))
            .withTmpFs(
                mapOf(
                    TEMPORARY_DIRECTORY to
                        "rw,noexec,nosuid,size=${config.temporaryStorageBytes},uid=10001,gid=10001,mode=1770"
                )
            )
        val environment = buildList {
            config.environment.toSortedMap().forEach { (name, value) -> add("$name=$value") }
            add("EDB_WORKER_HOST=0.0.0.0")
            add("EDB_WORKER_PORT=${config.containerPort}")
            add("EDB_WORKER_MAX_UPLOAD_BYTES=${config.maxUploadBytes}")
            config.bearerToken?.let { add("EDB_WORKER_BEARER_TOKEN=$it") }
        }
        return operation("create") {
            docker.createContainerCmd(config.image)
                .withName(config.containerName)
                .withLabels(config.labels + managedLabels(config.workerKind))
                .withEnv(environment)
                .withExposedPorts(exposedPort)
                .withHostConfig(hostConfig)
                .exec()
                .id
        }
    }

    override fun inspect(containerId: String): DockerWorkerContainerSnapshot? = try {
        val response = docker.inspectContainerCmd(containerId).exec()
        DockerWorkerContainerSnapshot(
            id = response.id,
            name = response.name.removePrefix("/"),
            image = response.config.image.orEmpty(),
            labels = response.config.labels.orEmpty(),
            running = response.state.running == true,
            publishedPorts = response.hostConfig.portBindings?.bindings.orEmpty().flatMap { (exposed, bindings) ->
                bindings.orEmpty().mapNotNull { binding ->
                    binding.hostPortSpec?.toIntOrNull()?.let { hostPort ->
                        DockerWorkerPublishedPort(
                            bindAddress = binding.hostIp.orEmpty().ifBlank { "0.0.0.0" },
                            hostPort = hostPort,
                            containerPort = exposed.port
                        )
                    }
                }
            }.toSet(),
            exitCode = response.state.exitCodeLong,
            error = response.state.error?.takeIf { it.isNotBlank() }
        )
    } catch (_: NotFoundException) {
        null
    } catch (failure: Exception) {
        throw DockerWorkerExceptionClassifier.classify("inspect", failure)
    }

    override fun findRunningByPublishedPort(
        bindAddress: String,
        hostPort: Int
    ): DockerWorkerContainerSnapshot? = operation("inspect-ports") {
        docker.listContainersCmd().withShowAll(false).exec()
            .asSequence()
            .mapNotNull { inspect(it.id) }
            .firstOrNull { container ->
                container.publishedPorts.any { published ->
                    published.hostPort == hostPort && addressesOverlap(published.bindAddress, bindAddress)
                }
            }
    }

    override fun start(containerId: String) {
        operation("start") { docker.startContainerCmd(containerId).exec() }
    }

    override fun stop(containerId: String, timeoutSeconds: Int) {
        operation("stop") { docker.stopContainerCmd(containerId).withTimeout(timeoutSeconds).exec() }
    }

    override fun rename(containerId: String, name: String) { operation("rename") { docker.renameContainerCmd(containerId).withName(name).exec() } }
    override fun remove(containerId: String, force: Boolean) { operation("remove") { docker.removeContainerCmd(containerId).withForce(force).exec() } }

    override fun logs(containerId: String, options: DockerWorkerLogOptions): String = operation("logs") {
        val content = StringBuilder()
        val callback = object : ResultCallback.Adapter<Frame>() {
            override fun onNext(frame: Frame) {
                content.append(String(frame.payload, StandardCharsets.UTF_8))
            }
        }
        val result = docker.logContainerCmd(containerId)
            .withFollowStream(false)
            .withStdOut(options.includeStdout)
            .withStdErr(options.includeStderr)
            .withTimestamps(options.includeTimestamps)
            .withTail(options.tailLines)
            .exec(callback)
        try {
            if (!result.awaitCompletion(options.timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw DockerWorkerLogTimeoutException(
                    "Docker worker log request exceeded ${options.timeout.toMillis()} ms."
                )
            }
        } finally {
            result.close()
        }
        content.toString()
    }

    private inline fun <T> operation(name: String, block: () -> T): T = try {
        block()
    } catch (failure: Exception) {
        throw DockerWorkerExceptionClassifier.classify(name, failure)
    }

    private fun addressesOverlap(existing: String, requested: String): Boolean =
        existing in WILDCARD_ADDRESSES || requested in WILDCARD_ADDRESSES || existing == requested

    companion object {
        const val MANAGED_LABEL = "me.moiclathy.edb.worker.managed"
        const val WORKER_KIND_LABEL = "me.moiclathy.edb.worker.kind"
        const val WORKER_KIND = "python-http"
        const val TEMPORARY_DIRECTORY = "/var/tmp/edb-worker"
        private const val NANO_CPUS_PER_CPU = 1_000_000_000L
        private val WILDCARD_ADDRESSES = setOf("", "0.0.0.0", "::")

        fun managedLabels(workerKind: String) = mapOf(
            MANAGED_LABEL to "true",
            WORKER_KIND_LABEL to workerKind
        )

        val MANAGED_LABELS = managedLabels(WORKER_KIND)
    }
}
