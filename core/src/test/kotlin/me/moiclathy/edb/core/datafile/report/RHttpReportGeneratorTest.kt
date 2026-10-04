package me.moiclathy.edb.core.datafile.report

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.datafile.analysis.ExternalHttpAnalysisHandler
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RHttpReportGeneratorTest {
    @Test
    fun `maps report definition and sources to external worker protocol`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestBody = ""
        server.createContext("/analyze") { exchange ->
            requestBody = String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)
            val body = "%PDF-test".toByteArray()
            exchange.responseHeaders.add(ExternalHttpAnalysisHandler.FILE_NAME_HEADER, "r-summary.pdf")
            exchange.responseHeaders.add("Content-Type", "application/pdf")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val generator = RHttpReportGenerator(RHttpReportGeneratorConfig(
                endpoint = URI.create("http://127.0.0.1:${server.address.port}/analyze")
            ))
            val definition = ReportDefinition(
                id = "experiment-summary",
                version = 2,
                title = "Experiment summary",
                generatorId = generator.id,
                outputFormat = DataFileFormat.PDF,
                outputFileName = "experiment.pdf"
            )
            val source = DataFile(
                id = 1,
                projectId = 7,
                createdByUserId = 3,
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
                listOf(ReportDataFileInput(source, ByteArrayInputStream("x\n1".toByteArray()))),
                mapOf("sampleRows" to "5")
            )).use { output ->
                assertEquals("r-summary.pdf", output.fileName)
                assertEquals("application/pdf", output.mediaType)
                assertEquals("%PDF-test", output.content.bufferedReader().readText())
            }

            assertTrue(requestBody.contains("\"handlerId\":\"r-tabular-summary-report\""))
            assertTrue(requestBody.contains("edb.report.definitionId"))
            assertTrue(requestBody.contains("experiment-summary"))
            assertTrue(requestBody.contains("experiment.pdf"))
        } finally {
            server.stop(0)
        }
    }
}
