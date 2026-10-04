package me.moiclathy.edb.web.bootstrap

import io.ktor.server.testing.testApplication
import me.moiclathy.edb.core.worker.docker.DockerWorkerRuntimeConfig
import me.moiclathy.edb.core.worker.docker.DockerWorkerRuntimeFactory
import me.moiclathy.edb.core.worker.docker.DockerWorkerStatus
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

class DockerWorkerWebLifecycleIntegrationTest {
    @Test
    fun `application shutdown stops its real managed worker container`() {
        assumeTrue(System.getenv("EDB_DOCKER_INTEGRATION") == "true", "Docker integration test is opt-in.")
        val lifecycle = DockerWorkerRuntimeFactory().create(
            DockerWorkerRuntimeConfig(
                image = "edb/python-worker:dev",
                containerName = "edb-python-worker-web-it",
                hostPort = 18082,
                bearerToken = "edb-integration-token",
                cpuLimit = 0.5,
                memoryLimitBytes = 512L * 1024 * 1024,
                temporaryStorageBytes = 64L * 1024 * 1024,
                readinessTimeout = Duration.ofSeconds(30),
                readinessPollInterval = Duration.ofMillis(250)
            )
        )

        testApplication {
            application {
                configureDockerWorkerLifecycle(managed = true) { lifecycle }
            }
            startApplication()
            assertEquals(DockerWorkerStatus.HEALTHY, lifecycle.state.value.status)
        }

        assertEquals(DockerWorkerStatus.STOPPED, lifecycle.state.value.status)
    }
}
