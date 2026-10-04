package me.moiclathy.edb.core.worker.docker

import me.moiclathy.edb.core.exception.EDBException

class DockerWorkerFleetMemberNotFoundException(workerId: String) : EDBException(
    "DOCKER_WORKER_NOT_FOUND",
    "Docker worker is not registered in the fleet: $workerId"
)

/** Application boundary used by operational APIs to manage the configured worker fleet. */
class DockerWorkerFleetApplicationService(
    private val fleet: DockerWorkerFleetLifecycle
) {
    suspend fun state(): DockerWorkerFleetState = fleet.statusAll()

    suspend fun start(): DockerWorkerFleetState = fleet.startAll()

    suspend fun stop(): DockerWorkerFleetState = fleet.stopAll()

    suspend fun restart(workerId: String): DockerWorkerState {
        requireMember(workerId)
        return fleet.restart(workerId)
    }

    suspend fun logs(
        workerId: String,
        options: DockerWorkerLogOptions = DockerWorkerLogOptions()
    ): DockerWorkerLogs {
        requireMember(workerId)
        return fleet.logs(workerId, options)
    }

    private fun requireMember(workerId: String) {
        if (workerId !in fleet.workerIds) throw DockerWorkerFleetMemberNotFoundException(workerId)
    }
}
