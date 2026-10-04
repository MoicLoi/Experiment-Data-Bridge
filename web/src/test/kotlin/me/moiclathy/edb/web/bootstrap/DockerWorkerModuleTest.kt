package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.DockerWorkerConfig
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

class DockerWorkerModuleTest {
    @Test
    fun `maps application configuration to runtime configuration`() {
        val runtime = DockerWorkerConfig(
            image = "registry/edb-worker:1.2.3",
            workerKind = "r-http",
            hostPort = 18000,
            cpuLimit = 1.5,
            readinessTimeoutMs = 12_000
        ).toRuntimeConfig()

        assertEquals("registry/edb-worker:1.2.3", runtime.image)
        assertEquals("r-http", runtime.workerKind)
        assertEquals(18000, runtime.hostPort)
        assertEquals(1.5, runtime.cpuLimit)
        assertEquals(Duration.ofSeconds(12), runtime.readinessTimeout)
    }
}
