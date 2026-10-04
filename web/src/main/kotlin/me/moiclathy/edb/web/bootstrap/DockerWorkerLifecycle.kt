package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.config.AnalysisWorkerMode
import me.moiclathy.edb.core.worker.docker.DockerWorkerLifecycle
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetLifecycle
import me.moiclathy.edb.core.worker.docker.DockerWorkerRebuildApplicationService
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

/** Starts an explicitly enabled managed Docker worker and stops it with the Ktor process. */
fun Application.configureDockerWorkerLifecycle(
    managed: Boolean = get<AppConfig>().task.analysis.workerMode == AnalysisWorkerMode.MANAGED_DOCKER,
    runtimeProvider: () -> DockerWorkerLifecycle = { get() }
) {
    val logger = LoggerFactory.getLogger("DockerWorkerLifecycle")
    if (!managed) {
        logger.info("Analysis worker mode is EXTERNAL; Docker runtime will not be created.")
        return
    }
    val runtime = runtimeProvider()

    monitor.subscribe(ApplicationStarted) {
        val state = runBlocking { runtime.start() }
        logger.info(
            "Docker worker {} is {} at {}.",
            state.containerName,
            state.status,
            state.endpoint
        )
    }

    monitor.subscribe(ApplicationStopping) {
        logger.info("Stopping managed Docker worker gracefully...")
        try {
            runBlocking { runtime.stop() }
        } finally {
            runtime.close()
        }
        logger.info("Managed Docker worker stopped.")
    }
}

/** Starts every explicitly configured managed Docker worker and stops the complete fleet with Ktor. */
fun Application.configureDockerWorkerFleetLifecycle(
    managed: Boolean = get<AppConfig>().task.analysis.enabledDockerWorkers().isNotEmpty(),
    fleetProvider: () -> DockerWorkerFleetLifecycle = { get() },
    rebuildProvider: () -> DockerWorkerRebuildApplicationService = { get() }
) {
    val logger = LoggerFactory.getLogger("DockerWorkerFleetLifecycle")
    if (!managed) {
        logger.info("No managed Docker workers are configured; Docker fleet will not be created.")
        return
    }
    val fleet = fleetProvider()
    val rebuild = rebuildProvider()

    monitor.subscribe(ApplicationStarted) {
        val reconciled = runBlocking { rebuild.reconcile() }
        reconciled.forEach { deployment ->
            logger.info("Reconciled Docker deployment {} as {} using image {}.", deployment.workerId, deployment.status, deployment.image)
        }
        val state = runBlocking { fleet.startAll() }
        state.workers.forEach { (id, worker) ->
            logger.info("Docker worker {} ({}) is {} at {}.", id, worker.containerName, worker.status, worker.endpoint)
        }
    }

    monitor.subscribe(ApplicationStopping) {
        logger.info("Stopping managed Docker worker fleet gracefully...")
        try {
            runBlocking { fleet.stopAll() }
        } finally {
            fleet.close()
        }
        logger.info("Managed Docker worker fleet stopped.")
    }
}
