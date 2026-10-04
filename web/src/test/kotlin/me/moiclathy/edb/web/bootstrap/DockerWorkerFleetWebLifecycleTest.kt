package me.moiclathy.edb.web.bootstrap

import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetLifecycle
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetState
import me.moiclathy.edb.core.worker.docker.DockerWorkerLogOptions
import me.moiclathy.edb.core.worker.docker.DockerWorkerLogs
import me.moiclathy.edb.core.worker.docker.DockerWorkerState
import me.moiclathy.edb.core.worker.docker.DockerWorkerStatus
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class DockerWorkerFleetWebLifecycleTest {
    @Test
    fun `starts stops and closes configured fleet with application`() {
        val fleet = RecordingFleet()

        testApplication {
            application { configureDockerWorkerFleetLifecycle(managed = true) { fleet } }
            startApplication()
            assertEquals(1, fleet.startCount.get())
        }

        assertEquals(1, fleet.stopCount.get())
        assertEquals(1, fleet.closeCount.get())
    }

    @Test
    fun `does not resolve fleet when no managed workers exist`() {
        val providerCalls = AtomicInteger()
        testApplication {
            application {
                configureDockerWorkerFleetLifecycle(managed = false) {
                    providerCalls.incrementAndGet()
                    RecordingFleet()
                }
            }
            startApplication()
        }
        assertEquals(0, providerCalls.get())
    }

    private class RecordingFleet : DockerWorkerFleetLifecycle {
        private val worker = DockerWorkerState(
            DockerWorkerStatus.STOPPED,
            "edb-r-worker",
            URI.create("http://127.0.0.1:8001")
        )
        private val mutableState = MutableStateFlow(DockerWorkerFleetState(mapOf("r" to worker)))
        override val state: StateFlow<DockerWorkerFleetState> = mutableState
        override val workerIds: Set<String> = setOf("r")
        val startCount = AtomicInteger()
        val stopCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override suspend fun ensureCreatedAll(): DockerWorkerFleetState = mutableState.value
        override suspend fun startAll(): DockerWorkerFleetState {
            startCount.incrementAndGet()
            return mutableState.value
        }
        override suspend fun stopAll(): DockerWorkerFleetState {
            stopCount.incrementAndGet()
            return mutableState.value
        }
        override suspend fun statusAll(): DockerWorkerFleetState = mutableState.value
        override suspend fun restart(workerId: String): DockerWorkerState = worker
        override suspend fun logs(workerId: String, options: DockerWorkerLogOptions) = DockerWorkerLogs("r", "")
        override fun close() { closeCount.incrementAndGet() }
    }
}
