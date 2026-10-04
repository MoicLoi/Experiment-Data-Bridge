package me.moiclathy.edb.core.worker.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockerJavaWorkerContainerGatewayTest {
    @Test
    fun `maps resource ports environment token labels and security to Docker create command`() {
        val docker = mockk<DockerClient>()
        val command = mockk<CreateContainerCmd>()
        val response = mockk<CreateContainerResponse>()
        val environment = slot<List<String>>()
        val labels = slot<Map<String, String>>()
        val hostConfig = slot<HostConfig>()
        every { docker.createContainerCmd("edb/python-worker:test") } returns command
        every { command.withName("edb-python-worker") } returns command
        every { command.withLabels(capture(labels)) } returns command
        every { command.withEnv(capture(environment)) } returns command
        every { command.withExposedPorts(any<ExposedPort>()) } returns command
        every { command.withHostConfig(capture(hostConfig)) } returns command
        every { command.exec() } returns response
        every { response.id } returns "container-42"
        val config = DockerWorkerRuntimeConfig(
            image = "edb/python-worker:test",
            workerKind = "r-http",
            hostPort = 18000,
            containerPort = 8000,
            bearerToken = "secret-token",
            environment = mapOf("ANALYSIS_PROFILE" to "production"),
            labels = mapOf("team" to "research"),
            cpuLimit = 1.5,
            memoryLimitBytes = 2_147_483_648
        )

        val id = DockerJavaWorkerContainerGateway(docker).create(config)

        assertEquals("container-42", id)
        assertTrue("ANALYSIS_PROFILE=production" in environment.captured)
        assertTrue("EDB_WORKER_BEARER_TOKEN=secret-token" in environment.captured)
        assertEquals("research", labels.captured["team"])
        assertEquals("true", labels.captured[DockerJavaWorkerContainerGateway.MANAGED_LABEL])
        assertEquals("r-http", labels.captured[DockerJavaWorkerContainerGateway.WORKER_KIND_LABEL])
        assertEquals(1_500_000_000L, hostConfig.captured.nanoCPUs)
        assertEquals(2_147_483_648L, hostConfig.captured.memory)
        assertEquals(true, hostConfig.captured.readonlyRootfs)
        assertFalse(hostConfig.captured.portBindings.bindings.isEmpty())
    }

    @Test
    fun `rejects custom values that could override reserved runtime contract`() {
        val failure = kotlin.test.assertFailsWith<IllegalArgumentException> {
            DockerWorkerRuntimeConfig(
                image = "edb/python-worker:test",
                environment = mapOf("EDB_WORKER_BEARER_TOKEN" to "spoofed")
            )
        }

        assertTrue(failure.message.orEmpty().contains("reserved"))
    }
}
