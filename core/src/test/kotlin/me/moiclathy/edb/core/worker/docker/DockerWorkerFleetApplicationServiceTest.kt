package me.moiclathy.edb.core.worker.docker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DockerWorkerFleetApplicationServiceTest {
    @Test
    fun `delegates fleet controls and rejects unknown members`() = runBlocking {
        val fleet = RecordingFleet()
        val service = DockerWorkerFleetApplicationService(fleet)

        assertEquals(DockerWorkerStatus.HEALTHY, service.state().workers.getValue("python").status)
        service.start()
        service.restart("python")
        service.logs("python")
        service.stop()

        assertEquals(listOf("status", "start", "restart:python", "logs:python", "stop"), fleet.operations)
        assertFailsWith<DockerWorkerFleetMemberNotFoundException> { service.restart("missing") }
        Unit
    }

    private class RecordingFleet : DockerWorkerFleetLifecycle {
        private val worker = DockerWorkerState(
            status = DockerWorkerStatus.HEALTHY,
            containerName = "edb-python-worker",
            endpoint = URI("http://127.0.0.1:8000"),
            containerId = "container-1",
            healthy = true
        )
        private val mutableState = MutableStateFlow(DockerWorkerFleetState(mapOf("python" to worker)))
        override val state: StateFlow<DockerWorkerFleetState> = mutableState
        override val workerIds = setOf("python")
        val operations = mutableListOf<String>()

        override suspend fun ensureCreatedAll() = mutableState.value
        override suspend fun startAll() = mutableState.value.also { operations += "start" }
        override suspend fun stopAll() = mutableState.value.also { operations += "stop" }
        override suspend fun statusAll() = mutableState.value.also { operations += "status" }
        override suspend fun restart(workerId: String) = worker.also { operations += "restart:$workerId" }
        override suspend fun logs(workerId: String, options: DockerWorkerLogOptions) =
            DockerWorkerLogs("container-1", "ready").also { operations += "logs:$workerId" }
        override fun close() = Unit
    }
}
