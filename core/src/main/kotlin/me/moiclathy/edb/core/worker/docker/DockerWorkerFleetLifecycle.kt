package me.moiclathy.edb.core.worker.docker

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import java.time.Instant

data class DockerWorkerFleetMember(
    val id: String,
    val lifecycle: DockerWorkerLifecycle
) {
    init {
        require(ID.matches(id)) { "Docker worker fleet member id is invalid: $id" }
    }

    companion object {
        private val ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
    }
}

data class DockerWorkerFleetState(
    val workers: Map<String, DockerWorkerState>,
    val observedAt: Instant = Instant.now()
) {
    val healthy: Boolean get() = workers.isNotEmpty() && workers.values.all { it.healthy }
}

class DockerWorkerFleetException(
    val operation: String,
    val failures: Map<String, Throwable>
) : IllegalStateException(
    "Docker worker fleet operation '$operation' failed for: ${failures.keys.sorted().joinToString()}"
) {
    init {
        require(failures.isNotEmpty()) { "Fleet failures must not be empty." }
        failures.toSortedMap().forEach { (id, failure) ->
            addSuppressed(IllegalStateException("Docker worker '$id' failed during '$operation'.", failure))
        }
    }
}

interface DockerWorkerFleetLifecycle : AutoCloseable {
    val state: StateFlow<DockerWorkerFleetState>
    val workerIds: Set<String>

    suspend fun ensureCreatedAll(): DockerWorkerFleetState
    suspend fun startAll(): DockerWorkerFleetState
    suspend fun stopAll(): DockerWorkerFleetState
    suspend fun statusAll(): DockerWorkerFleetState
    suspend fun restart(workerId: String): DockerWorkerState
    suspend fun logs(workerId: String, options: DockerWorkerLogOptions = DockerWorkerLogOptions()): DockerWorkerLogs
    suspend fun replace(member: DockerWorkerFleetMember) {
        throw UnsupportedOperationException("This Docker worker fleet does not support runtime member replacement.")
    }
}

class DefaultDockerWorkerFleetLifecycle(
    members: Iterable<DockerWorkerFleetMember>
) : DockerWorkerFleetLifecycle {
    private val allMembers = members.toList()
    private val membersById = allMembers.associateBy { it.id }.toSortedMap()
    private val mutableState: MutableStateFlow<DockerWorkerFleetState>

    init {
        require(membersById.size == allMembers.size) { "Docker worker fleet member ids must be unique." }
        val containerNames = allMembers.map { it.lifecycle.state.value.containerName }
        require(containerNames.distinct().size == containerNames.size) {
            "Docker worker fleet container names must be unique."
        }
        val endpoints = allMembers.map { it.lifecycle.state.value.endpoint }
        require(endpoints.distinct().size == endpoints.size) {
            "Docker worker fleet endpoints must be unique."
        }
        mutableState = MutableStateFlow(snapshot())
    }

    override val state: StateFlow<DockerWorkerFleetState> = mutableState.asStateFlow()
    override val workerIds: Set<String> = membersById.keys.toSet()

    override suspend fun ensureCreatedAll(): DockerWorkerFleetState = execute("ensure-created") {
        ensureCreated()
    }

    override suspend fun startAll(): DockerWorkerFleetState {
        val results = executeResults { start() }
        publishResults(results)
        val failures = failures(results)
        if (failures.isNotEmpty()) {
            val started = results.filterValues { it.isSuccess }.keys
            val rollback = executeResults(started) { stop() }
            publishResults(rollback)
            val rollbackFailures = failures(rollback).mapKeys { (id, _) -> "$id:rollback" }
            throw DockerWorkerFleetException("start", failures + rollbackFailures)
        }
        return mutableState.value
    }

    override suspend fun stopAll(): DockerWorkerFleetState = execute("stop") { stop() }

    override suspend fun statusAll(): DockerWorkerFleetState = execute("status") { status() }

    override suspend fun restart(workerId: String): DockerWorkerState = requireMember(workerId)
        .restart()
        .also { publish(workerId, it) }

    override suspend fun logs(workerId: String, options: DockerWorkerLogOptions): DockerWorkerLogs =
        requireMember(workerId).logs(options)

    override suspend fun replace(member: DockerWorkerFleetMember) {
        val previous = synchronized(membersById) { membersById.put(member.id, member) }
        previous?.lifecycle?.close()
        publish(member.id, member.lifecycle.status())
    }

    override fun close() {
        val failures = linkedMapOf<String, Throwable>()
        membersById.forEach { (id, member) ->
            runCatching { member.lifecycle.close() }.exceptionOrNull()?.let { failures[id] = it }
        }
        if (failures.isNotEmpty()) throw DockerWorkerFleetException("close", failures)
    }

    private suspend fun execute(
        operation: String,
        block: suspend DockerWorkerLifecycle.() -> DockerWorkerState
    ): DockerWorkerFleetState {
        val results = executeResults(block = block)
        publishResults(results)
        failures(results).takeIf { it.isNotEmpty() }?.let { throw DockerWorkerFleetException(operation, it) }
        return mutableState.value
    }

    private suspend fun executeResults(
        ids: Set<String> = workerIds,
        block: suspend DockerWorkerLifecycle.() -> DockerWorkerState
    ): Map<String, Result<DockerWorkerState>> = supervisorScope {
        ids.sorted().map { id ->
            async { id to runCatching { block(requireMember(id)) } }
        }.awaitAll().toMap()
    }

    private fun failures(results: Map<String, Result<DockerWorkerState>>): Map<String, Throwable> =
        results.mapNotNull { (id, result) -> result.exceptionOrNull()?.let { id to it } }.toMap()

    private fun publishResults(results: Map<String, Result<DockerWorkerState>>) {
        results.forEach { (id, result) -> result.getOrNull()?.let { publish(id, it) } }
    }

    private fun publish(id: String, worker: DockerWorkerState) {
        mutableState.value = DockerWorkerFleetState(mutableState.value.workers + (id to worker))
    }

    private fun snapshot() = DockerWorkerFleetState(
        membersById.mapValues { (_, member) -> member.lifecycle.state.value }
    )

    private fun requireMember(id: String): DockerWorkerLifecycle =
        membersById[id]?.lifecycle ?: throw NoSuchElementException("Docker worker is not registered in the fleet: $id")
}
