package me.moiclathy.edb.core.worker.docker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DockerWorkerFleetLifecycleTest {
    @Test
    fun `starts statuses and stops all workers`() = runBlocking {
        val python = RecordingLifecycle("python", 8000)
        val r = RecordingLifecycle("r", 8001)
        val fleet = fleet(python, r)

        val started = fleet.startAll()
        val statuses = fleet.statusAll()
        val stopped = fleet.stopAll()

        assertTrue(started.healthy)
        assertEquals(setOf("python", "r"), started.workers.keys)
        assertEquals(2, statuses.workers.size)
        assertTrue(stopped.workers.values.all { it.status == DockerWorkerStatus.STOPPED })
        assertEquals(1, python.startCount.get())
        assertEquals(1, r.startCount.get())
        assertEquals(1, python.stopCount.get())
        assertEquals(1, r.stopCount.get())
    }

    @Test
    fun `rolls back workers that started when another member fails`() = runBlocking {
        val python = RecordingLifecycle("python", 8000)
        val r = RecordingLifecycle("r", 8001, failStart = true)
        val fleet = fleet(python, r)

        val failure = assertFailsWith<DockerWorkerFleetException> { fleet.startAll() }

        assertEquals(setOf("r"), failure.failures.keys)
        assertEquals(1, python.stopCount.get())
        assertEquals(DockerWorkerStatus.STOPPED, fleet.state.value.workers.getValue("python").status)
    }

    @Test
    fun `targets restart and logs by stable worker id`() = runBlocking {
        val python = RecordingLifecycle("python", 8000)
        val r = RecordingLifecycle("r", 8001)
        val fleet = fleet(python, r)

        val restarted = fleet.restart("r")
        val logs = fleet.logs("r")

        assertEquals("edb-r-worker", restarted.containerName)
        assertEquals("r logs", logs.content)
        assertEquals(0, python.restartCount.get())
        assertEquals(1, r.restartCount.get())
    }

    private fun fleet(vararg lifecycles: RecordingLifecycle) = DefaultDockerWorkerFleetLifecycle(
        lifecycles.map { DockerWorkerFleetMember(it.id, it) }
    )

    private class RecordingLifecycle(
        val id: String,
        private val port: Int,
        private val failStart: Boolean = false
    ) : DockerWorkerLifecycle {
        private val mutableState = MutableStateFlow(state(DockerWorkerStatus.STOPPED))
        override val state: StateFlow<DockerWorkerState> = mutableState
        val startCount = AtomicInteger()
        val stopCount = AtomicInteger()
        val restartCount = AtomicInteger()

        override suspend fun ensureCreated(): DockerWorkerState = state(DockerWorkerStatus.CREATED).also {
            mutableState.value = it
        }

        override suspend fun start(): DockerWorkerState {
            startCount.incrementAndGet()
            if (failStart) throw IllegalStateException("$id failed")
            return state(DockerWorkerStatus.HEALTHY).also { mutableState.value = it }
        }

        override suspend fun stop(): DockerWorkerState {
            stopCount.incrementAndGet()
            return state(DockerWorkerStatus.STOPPED).also { mutableState.value = it }
        }

        override suspend fun restart(): DockerWorkerState {
            restartCount.incrementAndGet()
            return state(DockerWorkerStatus.HEALTHY).also { mutableState.value = it }
        }

        override suspend fun status(): DockerWorkerState = mutableState.value

        override suspend fun logs(options: DockerWorkerLogOptions): DockerWorkerLogs =
            DockerWorkerLogs("$id-container", "$id logs")

        override fun close() = Unit

        private fun state(status: DockerWorkerStatus) = DockerWorkerState(
            status = status,
            containerName = "edb-$id-worker",
            endpoint = URI.create("http://127.0.0.1:$port"),
            healthy = status == DockerWorkerStatus.HEALTHY
        )
    }
}
