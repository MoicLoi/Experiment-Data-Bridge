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

class RWorkerDockerIntegrationTest {
    @Test
    fun `managed R worker becomes healthy and generates PDF report`() = runBlocking {
        assumeTrue(System.getenv("EDB_R_DOCKER_INTEGRATION") == "true", "R Docker integration test is opt-in.")
        val token = "edb-r-docker-integration-token"
        val port = 18084
        val lifecycle = DockerWorkerRuntimeFactory().create(DockerWorkerRuntimeConfig(
            image = "edb/r-worker:dev",
            workerKind = "r-http",
            containerName = "edb-r-worker-it",
            hostPort = port,
            containerPort = 8001,
            bearerToken = token,
            readinessTimeout = Duration.ofMinutes(2)
        ))
        try {
            val state = lifecycle.start()
            assertEquals(DockerWorkerStatus.HEALTHY, state.status)
            assertTrue(state.healthy)

            val generator = RHttpReportGenerator(RHttpReportGeneratorConfig(
                endpoint = URI.create("http://127.0.0.1:$port/analyze"),
                bearerToken = token
            ))
            val definition = ReportDefinition(
                id = "r-docker-summary",
                title = "R Docker summary",
                generatorId = generator.id,
                outputFormat = DataFileFormat.PDF,
                outputFileName = "r-docker-summary.pdf"
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
                listOf(ReportDataFileInput(
                    source,
                    ByteArrayInputStream("x,y,label\n1,2,a\n3,,b\n".toByteArray())
                )),
                emptyMap()
            )).use { output ->
                assertEquals("r-docker-summary.pdf", output.fileName)
                assertEquals("application/pdf", output.mediaType)
                assertEquals("%PDF", String(output.content.readNBytes(4)))
            }
        } finally {
            runCatching { lifecycle.stop() }
            lifecycle.close()
        }
    }
}
