package me.moiclathy.edb.core.worker.docker

import kotlinx.coroutines.runBlocking
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import me.moiclathy.edb.core.datafile.analysis.AnalysisDataFileInput
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandlerInput
import me.moiclathy.edb.core.datafile.analysis.PythonHttpAnalysisHandler
import me.moiclathy.edb.core.datafile.analysis.PythonHttpAnalysisHandlerConfig
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DockerWorkerIntegrationTest {
    @Test
    fun `manages real worker and executes Kotlin to Python analysis`() = runBlocking {
        requireDockerIntegration()
        val config = integrationConfig("edb-python-worker-core-it", 18081)
        val lifecycle = DockerWorkerRuntimeFactory().create(config)
        try {
            assertEquals(DockerWorkerStatus.HEALTHY, lifecycle.start().status)
            assertEquals(DockerWorkerStatus.HEALTHY, lifecycle.status().status)

            val handler = PythonHttpAnalysisHandler(
                PythonHttpAnalysisHandlerConfig(
                    endpoint = URI.create("${config.endpoint}/analyze"),
                    bearerToken = TOKEN
                )
            )
            val source = DataFile(
                id = 101,
                projectId = 7,
                createdByUserId = 3,
                kind = DataFileKind.RAW_DATA,
                format = DataFileFormat.HDF5,
                status = DataFileStatus.AVAILABLE,
                originalFileName = "observations.h5",
                mediaType = "application/x-hdf5",
                provenance = DataFileProvenance(DataFileOrigin.UPLOAD),
                createdAt = Instant.parse("2026-08-11T00:00:00Z")
            )
            val output = handler.analyze(
                AnalysisHandlerInput(
                    sources = listOf(
                        AnalysisDataFileInput(source, fixtureInput())
                    ),
                    parameters = mapOf("operation" to "hdf5-summary")
                )
            )
            output.use {
                val summary = it.content.bufferedReader().readText()
                assertEquals("application/json", it.mediaType)
                assertTrue(summary.contains("\"observations\""))
                assertTrue(summary.contains("\"min\": 1.0"))
                assertTrue(summary.contains("\"max\": 4.0"))
            }
            assertTrue(lifecycle.logs(DockerWorkerLogOptions(tailLines = 100)).content.contains("POST /analyze"))

            assertEquals(DockerWorkerStatus.STOPPED, lifecycle.stop().status)
            val restarted = lifecycle.restart()
            assertEquals(DockerWorkerStatus.HEALTHY, restarted.status)

            killContainer(restarted.containerId!!)
            assertEquals(DockerWorkerStatus.HEALTHY, lifecycle.start().status)
        } finally {
            runCatching { lifecycle.stop() }
            lifecycle.close()
        }
    }

    @Test
    fun `classifies a real host port conflict and keeps first worker healthy`() = runBlocking {
        requireDockerIntegration()
        val first = DockerWorkerRuntimeFactory().create(integrationConfig("edb-python-worker-port-owner-it", 18083))
        val second = DockerWorkerRuntimeFactory().create(integrationConfig("edb-python-worker-port-conflict-it", 18083))
        try {
            assertEquals(DockerWorkerStatus.HEALTHY, first.start().status)
            val failure = assertFailsWith<DockerWorkerException> { second.start() }
            assertTrue(failure.category != DockerWorkerFailureCategory.UNKNOWN)
            assertEquals(DockerWorkerStatus.HEALTHY, first.status().status)
        } finally {
            runCatching { second.stop() }
            runCatching { first.stop() }
            second.close()
            first.close()
        }
    }

    private fun integrationConfig(name: String, port: Int) = DockerWorkerRuntimeConfig(
        image = "edb/python-worker:dev",
        containerName = name,
        hostPort = port,
        bearerToken = TOKEN,
        cpuLimit = 0.75,
        memoryLimitBytes = 768L * 1024 * 1024,
        temporaryStorageBytes = 128L * 1024 * 1024,
        readinessTimeout = Duration.ofSeconds(30),
        readinessPollInterval = Duration.ofMillis(250)
    )

    private fun requireDockerIntegration() {
        assumeTrue(System.getenv("EDB_DOCKER_INTEGRATION") == "true", "Docker integration test is opt-in.")
    }

    private fun fixtureInput() = GZIPInputStream(
        ByteArrayInputStream(Base64.getDecoder().decode(GZIP_HDF5_FIXTURE))
    )

    private fun killContainer(containerId: String) {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
        val transport = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .build()
        DockerClientImpl.getInstance(config, transport).use { docker ->
            docker.killContainerCmd(containerId).exec()
        }
    }

    companion object {
        private const val TOKEN = "edb-integration-token"
        private const val GZIP_HDF5_FIXTURE =
            "H4sIAAAAAAACA+v0cHHj5ZLiYubgYICB/1AgABUygIo/jptT5u/hEsTEUMEkhKwQBriYGBgZGNjEGYAUT35ScWpRWWJJZn5e8WaY0bYMlIFlri/egJ3AyMDIKMLAwMTIyMgElYPRzCIMjIIK9gwQ1zswmHAzmPxnZmBgBbqOmYsD6HJmRqgkgwDM4A0MQw5kO7kdZxgFo2AUjIJRMArIBx/sodWhAwBmL6bEEAgAAA=="
        private const val HDF5_FIXTURE =
            "iUhERg0KGgoAAAAAAAgIAAQAEAAAAAAAAAAAAAAAAAD//////////yAIAAAAAAAA//////////8AAAAAAAAAAGAAAAAAAAAAAQAAAAAAAACIAAAAAAAAAKgCAAAAAAAAAQABAAEAAAAYAAAAAAAAABEAEAAAAAAAiAAAAAAAAACoAgAAAAAAAFRSRUUAAAEA/////////////////////wAAAAAAAAAAMAQAAAAAAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABIRUFQAAAAAFgAAAAAAAAAGAAAAAAAAADIAgAAAAAAAAAAAAAAAAAAb2JzZXJ2YXRpb25zAAAAAAEAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAUAAQAAAAABAAAAAAAAAQAoAAAAAAABAgEAAAAAAAIAAAAAAAAAAgAAAAAAAAACAAAAAAAAAAIAAAAAAAAAAwAYAAEAAAARID8ACAAAAAAAQAA0CwA0/wMAAAAAAAAFAAgAAQAAAAICAgEAAAAACAAYAAAAAAADAQAIAAAAAAAAIAAAAAAAAAAAAAAAAAAAAHgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFNOT0QBAAEACAAAAAAAAAAgAwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADwPwAAAAAAAABAAAAAAAAACEAAAAAAAAAQQA=="
    }
}
