package me.moiclathy.edb.web.bootstrap

import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.moiclathy.edb.core.worker.docker.DockerWorkerLifecycle
import me.moiclathy.edb.core.worker.docker.DockerWorkerLogOptions
import me.moiclathy.edb.core.worker.docker.DockerWorkerLogs
import me.moiclathy.edb.core.worker.docker.DockerWorkerState
import me.moiclathy.edb.core.worker.docker.DockerWorkerStatus
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class DockerWorkerLifecycleTest {
    @Test
    fun `starts stops and closes enabled Docker worker with application lifecycle`() {
        val runtime = RecordingDockerWorkerLifecycle()

        testApplication {
            application {
                configureDockerWorkerLifecycle(managed = true) { runtime }
            }
            startApplication()
            assertEquals(1, runtime.startCount.get())
            assertEquals(DockerWorkerStatus.HEALTHY, runtime.state.value.status)
        }

        assertEquals(1, runtime.stopCount.get())
        assertEquals(1, runtime.closeCount.get())
        assertEquals(DockerWorkerStatus.STOPPED, runtime.state.value.status)
    }

    @Test
    fun `does not create Docker client in external worker mode`() {
        val providerCalls = AtomicInteger()

        testApplication {
            application {
                configureDockerWorkerLifecycle(managed = false) {
                    providerCalls.incrementAndGet()
                    RecordingDockerWorkerLifecycle()
                }
            }
            startApplication()
        }

        assertEquals(0, providerCalls.get())
    }

    private class RecordingDockerWorkerLifecycle : DockerWorkerLifecycle {
        private val mutableState = MutableStateFlow(state(DockerWorkerStatus.STOPPED))
        override val state: StateFlow<DockerWorkerState> = mutableState
        val startCount = AtomicInteger()
        val stopCount = AtomicInteger()
        val closeCount = AtomicInteger()

        override suspend fun ensureCreated(): DockerWorkerState = mutableState.value

        override suspend fun start(): DockerWorkerState {
            startCount.incrementAndGet()
            return state(DockerWorkerStatus.HEALTHY).also { mutableState.value = it }
        }

        override suspend fun stop(): DockerWorkerState {
            stopCount.incrementAndGet()
            return state(DockerWorkerStatus.STOPPED).also { mutableState.value = it }
        }

        override suspend fun restart(): DockerWorkerState = start()

        override suspend fun status(): DockerWorkerState = mutableState.value

        override suspend fun logs(options: DockerWorkerLogOptions): DockerWorkerLogs =
            DockerWorkerLogs("container-1", "")

        override fun close() {
            closeCount.incrementAndGet()
        }

        private fun state(status: DockerWorkerStatus) = DockerWorkerState(
            status = status,
            containerName = "edb-python-worker",
            endpoint = URI.create("http://127.0.0.1:8000"),
            healthy = status == DockerWorkerStatus.HEALTHY
        )
    }
}
