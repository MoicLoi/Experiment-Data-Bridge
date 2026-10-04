package me.moiclathy.edb.core.datafile.analysis

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
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

class ExternalHttpAnalysisHandlerTest {
    @Test
    fun `generic R handler uses protocol without Python coupling`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestBody = ""
        server.createContext("/analyze") { exchange ->
            requestBody = String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)
            val body = "{\"rows\":2}".toByteArray()
            exchange.responseHeaders.add(ExternalHttpAnalysisHandler.FILE_NAME_HEADER, "r-summary.json")
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val handler = ExternalHttpAnalysisHandler(ExternalHttpAnalysisHandlerConfig(
                id = "r-statistical-summary",
                analysisType = "statistics",
                tool = "r-http",
                endpoint = URI.create("http://127.0.0.1:${server.address.port}/analyze"),
                outputFormat = DataFileFormat.JSON
            ))
            val source = DataFile(
                id = 42,
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

            handler.analyze(AnalysisHandlerInput(
                listOf(AnalysisDataFileInput(source, ByteArrayInputStream("x\n1\n2".toByteArray()))),
                mapOf("confidenceLevel" to "0.95")
            )).use { output ->
                assertEquals("r-summary.json", output.fileName)
                assertEquals("{\"rows\":2}", output.content.bufferedReader().readText())
            }

            assertEquals("r-statistical-summary", handler.id)
            assertEquals("statistics", handler.analysisType)
            assertEquals("r-http", handler.tool)
            assertTrue(requestBody.contains("\"handlerId\":\"r-statistical-summary\""))
            assertTrue(requestBody.contains("confidenceLevel"))
        } finally {
            server.stop(0)
        }
    }
}
