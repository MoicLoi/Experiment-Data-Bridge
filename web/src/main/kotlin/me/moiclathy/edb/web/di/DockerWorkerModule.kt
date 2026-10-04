package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.config.DockerWorkerConfig
import me.moiclathy.edb.core.worker.docker.DockerWorkerLifecycle
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetApplicationService
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetLifecycle
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetMember
import me.moiclathy.edb.core.worker.docker.DefaultDockerWorkerFleetLifecycle
import me.moiclathy.edb.core.worker.docker.DockerWorkerRuntimeConfig
import me.moiclathy.edb.core.worker.docker.DockerWorkerRuntimeFactory
import me.moiclathy.edb.core.worker.docker.registry.DockerRegistryProfileRepository
import me.moiclathy.edb.core.worker.docker.registry.MySqlDockerRegistryProfileRepository
import me.moiclathy.edb.core.worker.docker.registry.DockerRegistryProfileService
import me.moiclathy.edb.core.worker.docker.registry.DockerRegistryCredentialResolver
import me.moiclathy.edb.core.worker.docker.registry.EnvironmentDockerRegistryCredentialResolver
import me.moiclathy.edb.core.worker.docker.registry.DockerWorkerDeploymentRepository
import me.moiclathy.edb.core.worker.docker.registry.MySqlDockerWorkerDeploymentRepository
import me.moiclathy.edb.core.worker.docker.DockerWorkerRebuildApplicationService
import org.koin.dsl.module
import org.koin.core.module.dsl.singleOf
import java.time.Duration

val dockerWorkerModule = module {
    single<DockerRegistryProfileRepository> { MySqlDockerRegistryProfileRepository() }
    single<DockerRegistryCredentialResolver> { EnvironmentDockerRegistryCredentialResolver() }
    single<DockerWorkerDeploymentRepository> { MySqlDockerWorkerDeploymentRepository() }
    single { DockerRegistryProfileService(get()) }
    single { DockerWorkerRuntimeFactory() }
    single<DockerWorkerLifecycle> {
        val config = get<AppConfig>().task.analysis.dockerWorker
        get<DockerWorkerRuntimeFactory>().create(config.toRuntimeConfig())
    }
    single<DockerWorkerFleetLifecycle> {
        val factory = get<DockerWorkerRuntimeFactory>()
        DefaultDockerWorkerFleetLifecycle(
            get<AppConfig>().task.analysis.enabledDockerWorkers().map { config ->
                val runtime = config.toRuntimeConfig().let { base -> get<DockerWorkerDeploymentRepository>().find(config.id)?.let { base.copy(image = it.image) } ?: base }
                DockerWorkerFleetMember(config.id, factory.create(runtime))
            }
        )
    }
    singleOf(::DockerWorkerFleetApplicationService)
    single {
        DockerWorkerRebuildApplicationService(
            get<AppConfig>().task.analysis.enabledDockerWorkers().associate { it.id to it.toRuntimeConfig() }, get(), get(), get(), get(), get()
        )
    }
}

internal fun DockerWorkerConfig.toRuntimeConfig() = DockerWorkerRuntimeConfig(
    image = image,
    workerKind = workerKind,
    containerName = containerName,
    host = host,
    bindAddress = bindAddress,
    hostPort = hostPort,
    containerPort = containerPort,
    healthPath = healthPath,
    bearerToken = bearerToken,
    environment = environment,
    labels = labels,
    maxUploadBytes = maxUploadBytes,
    cpuLimit = cpuLimit,
    memoryLimitBytes = memoryLimitBytes,
    temporaryStorageBytes = temporaryStorageBytes,
    healthRequestTimeout = Duration.ofMillis(healthRequestTimeoutMs),
    readinessTimeout = Duration.ofMillis(readinessTimeoutMs),
    readinessPollInterval = Duration.ofMillis(readinessPollIntervalMs),
    stopTimeout = Duration.ofMillis(stopTimeoutMs)
)
