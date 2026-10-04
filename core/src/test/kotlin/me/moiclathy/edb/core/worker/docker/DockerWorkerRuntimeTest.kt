package me.moiclathy.edb.core.worker.docker

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DockerWorkerRuntimeTest {
    private val config = DockerWorkerRuntimeConfig(
        image = "edb/python-worker:test",
        readinessTimeout = Duration.ofSeconds(1),
        readinessPollInterval = Duration.ofMillis(1)
    )

    @Test
    fun `creates starts and waits for a healthy worker idempotently`() = runBlocking {
        val gateway = mockk<DockerWorkerContainerGateway>()
        val stopped = snapshot(running = false)
        val running = snapshot(running = true)
        every { gateway.findByName(config.containerName) } returnsMany listOf(null, running)
        every { gateway.create(config) } returns stopped.id
        every { gateway.inspect(stopped.id) } returnsMany listOf(stopped, running, running)
        every { gateway.findRunningByPublishedPort(config.bindAddress, config.hostPort) } returns null
        every { gateway.start(stopped.id) } returns Unit
        var healthChecks = 0
        val health = DockerWorkerHealthClient { _, _ ->
            healthChecks += 1
            DockerWorkerHealth(healthy = healthChecks >= 2, detail = "warming up")
        }
        val runtime = runtime(gateway, health)

        val first = runtime.start()
        val second = runtime.start()

        assertEquals(DockerWorkerStatus.HEALTHY, first.status)
        assertEquals(DockerWorkerStatus.HEALTHY, second.status)
        assertTrue(second.healthy)
        verify(exactly = 1) { gateway.create(config) }
        verify(exactly = 1) { gateway.start(stopped.id) }
    }

    @Test
    fun `stop is idempotent for a stopped worker`() = runBlocking {
        val gateway = mockk<DockerWorkerContainerGateway>()
        val running = snapshot(running = true)
        val stopped = snapshot(running = false)
        every { gateway.findByName(config.containerName) } returnsMany listOf(running, stopped)
        every { gateway.stop(running.id, 20) } returns Unit
        every { gateway.inspect(running.id) } returns stopped
        val runtime = runtime(gateway)

        assertEquals(DockerWorkerStatus.STOPPED, runtime.stop().status)
        assertEquals(DockerWorkerStatus.STOPPED, runtime.stop().status)

        verify(exactly = 1) { gateway.stop(running.id, 20) }
    }

    @Test
    fun `refuses to control an unowned container with the configured name`() = runBlocking {
        val gateway = mockk<DockerWorkerContainerGateway>()
        every { gateway.findByName(config.containerName) } returns snapshot(running = true, labels = emptyMap())
        val runtime = runtime(gateway)

        assertFailsWith<DockerWorkerOwnershipException> { runtime.start() }
        verify(exactly = 0) { gateway.start(any()) }
    }

    @Test
    fun `reports an unhealthy running worker without mutating its container`() = runBlocking {
        val gateway = mockk<DockerWorkerContainerGateway>()
        every { gateway.findByName(config.containerName) } returns snapshot(running = true)
        val runtime = runtime(gateway, DockerWorkerHealthClient { _, _ -> DockerWorkerHealth(false, "not ready") })

        val status = runtime.status()

        assertEquals(DockerWorkerStatus.UNHEALTHY, status.status)
        assertEquals("not ready", status.detail)
        verify(exactly = 0) { gateway.start(any()) }
        verify(exactly = 0) { gateway.stop(any(), any()) }
    }

    @Test
    fun `reads bounded logs only from the owned container`() = runBlocking {
        val gateway = mockk<DockerWorkerContainerGateway>()
        val options = DockerWorkerLogOptions(tailLines = 50, includeTimestamps = false)
        every { gateway.findByName(config.containerName) } returns snapshot(running = true)
        every { gateway.logs("container-1", options) } returns "worker ready\n"
        val runtime = runtime(gateway)

        val logs = runtime.logs(options)

        assertEquals("container-1", logs.containerId)
        assertEquals("worker ready\n", logs.content)
        verify(exactly = 1) { gateway.logs("container-1", options) }
    }

    private fun runtime(
        gateway: DockerWorkerContainerGateway,
        health: DockerWorkerHealthClient = DockerWorkerHealthClient { _, _ -> DockerWorkerHealth(true) }
    ) = DockerWorkerRuntime(config, gateway, health, ioDispatcher = Dispatchers.Unconfined)

    private fun snapshot(
        running: Boolean,
        labels: Map<String, String> = DockerJavaWorkerContainerGateway.MANAGED_LABELS
    ) = DockerWorkerContainerSnapshot(
        id = "container-1",
        name = config.containerName,
        image = config.image,
        labels = labels,
        running = running
    )
}
