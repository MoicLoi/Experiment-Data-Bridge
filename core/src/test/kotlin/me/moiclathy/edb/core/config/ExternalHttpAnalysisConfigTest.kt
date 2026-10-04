package me.moiclathy.edb.core.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExternalHttpAnalysisConfigTest {
    @Test
    fun `analysis configuration supports multiple protocol compatible handlers`() {
        val config = TaskAnalysisConfig(externalHttpHandlers = listOf(
            ExternalHttpAnalysisConfig(true, "r-summary", "statistics", "r-http", "http://localhost:8001/analyze"),
            ExternalHttpAnalysisConfig(true, "matlab", "numerical", "matlab-http", "http://localhost:8002/analyze")
        ))

        assertEquals(listOf("r-summary", "matlab"), config.externalHttpHandlers.map { it.handlerId })
    }

    @Test
    fun `external handler rejects invalid timeout`() {
        assertFailsWith<IllegalArgumentException> {
            ExternalHttpAnalysisConfig(
                handlerId = "r-summary",
                analysisType = "statistics",
                tool = "r-http",
                endpoint = "http://localhost/analyze",
                requestTimeoutMs = 0
            )
        }
    }

    @Test
    fun `managed worker fleet combines legacy worker and enabled additional workers`() {
        val config = TaskAnalysisConfig(
            workerMode = AnalysisWorkerMode.MANAGED_DOCKER,
            dockerWorker = DockerWorkerConfig(id = "python"),
            managedDockerWorkers = listOf(
                DockerWorkerConfig(id = "r", workerKind = "r-http", containerName = "edb-r-worker", hostPort = 8001),
                DockerWorkerConfig(id = "disabled", enabled = false, containerName = "disabled-worker", hostPort = 8002)
            )
        )

        assertEquals(listOf("python", "r"), config.enabledDockerWorkers().map { it.id })
    }

    @Test
    fun `managed worker fleet rejects duplicate enabled ids`() {
        assertFailsWith<IllegalArgumentException> {
            TaskAnalysisConfig(
                managedDockerWorkers = listOf(
                    DockerWorkerConfig(id = "r", containerName = "r-one", hostPort = 8001),
                    DockerWorkerConfig(id = "r", containerName = "r-two", hostPort = 8002)
                )
            )
        }
    }
}
