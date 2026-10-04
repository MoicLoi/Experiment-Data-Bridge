package me.moiclathy.edb.core.worker.docker

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.report.RHttpReportGenerator
import me.moiclathy.edb.core.datafile.report.RHttpReportGeneratorConfig
import me.moiclathy.edb.core.datafile.report.ReportDataFileInput
import me.moiclathy.edb.core.datafile.report.ReportDefinition
import me.moiclathy.edb.core.datafile.report.ReportGenerationInput
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DockerWorkerFleetIntegrationTest {
    @Test
    fun `fleet manages Python and R workers together`() = runBlocking {
        assumeTrue(System.getenv("EDB_DOCKER_FLEET_INTEGRATION") == "true", "Docker fleet integration test is opt-in.")
        val token = "edb-docker-fleet-integration-token"
        val factory = DockerWorkerRuntimeFactory()
        val fleet = DefaultDockerWorkerFleetLifecycle(listOf(
            DockerWorkerFleetMember("python", factory.create(config(
                image = "edb/python-worker:dev",
                kind = "python-http",
                name = "edb-python-worker-fleet-it",
                hostPort = 18085,
                containerPort = 8000,
                token = token
            ))),
            DockerWorkerFleetMember("r", factory.create(config(
                image = "edb/r-worker:dev",
                kind = "r-http",
                name = "edb-r-worker-fleet-it",
                hostPort = 18086,
                containerPort = 8001,
                token = token
            )))
        ))
        try {
            val started = fleet.startAll()
            assertEquals(setOf("python", "r"), started.workers.keys)
            assertTrue(started.healthy)
            assertTrue(fleet.statusAll().workers.values.all { it.status == DockerWorkerStatus.HEALTHY })

            val generator = RHttpReportGenerator(RHttpReportGeneratorConfig(
                endpoint = URI.create("http://127.0.0.1:18086/analyze"),
                bearerToken = token
            ))
            val definition = ReportDefinition(
                id = "fleet-summary",
                title = "Fleet summary",
                generatorId = generator.id,
                outputFormat = DataFileFormat.PDF,
                outputFileName = "fleet-summary.pdf"
            )
            val source = DataFile(
                id = 1,
                projectId = 1,
                createdByUserId = 1,
                kind = DataFileKind.RAW_DATA,
                format = DataFileFormat.CSV,
                status = DataFileStatus.AVAILABLE,
                originalFileName = "observations.csv",
                mediaType = "text/csv",
                provenance = DataFileProvenance(DataFileOrigin.UPLOAD),
                createdAt = Instant.EPOCH
            )
            generator.generate(ReportGenerationInput(
                definition,
                listOf(ReportDataFileInput(source, ByteArrayInputStream("x,y\n1,2\n".toByteArray()))),
                emptyMap()
            )).use { output -> assertEquals("%PDF", String(output.content.readNBytes(4))) }
            assertTrue(fleet.logs("r").content.contains("Running plumber API"))
        } finally {
            runCatching { fleet.stopAll() }
            fleet.close()
        }
    }

    private fun config(
        image: String,
        kind: String,
        name: String,
        hostPort: Int,
        containerPort: Int,
        token: String
    ) = DockerWorkerRuntimeConfig(
        image = image,
        workerKind = kind,
        containerName = name,
        hostPort = hostPort,
        containerPort = containerPort,
        bearerToken = token,
        readinessTimeout = Duration.ofMinutes(2)
    )
}
