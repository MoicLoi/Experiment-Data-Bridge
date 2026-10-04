package me.moiclathy.edb.core.datafile.analysis

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PythonHttpAnalysisHandlerTest {
    @Test
    fun `streams manifest and source to Python worker and exposes returned artifact`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestBody = ""
        var authorization: String? = null
        server.createContext("/analyze") { exchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            requestBody = String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)
            val metadata = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"score\":0.95}".toByteArray(StandardCharsets.UTF_8))
            val body = "{\"prediction\":\"ok\"}".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add(PythonHttpAnalysisHandler.FILE_NAME_HEADER, "prediction.json")
            exchange.responseHeaders.add(PythonHttpAnalysisHandler.METADATA_HEADER, metadata)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val handler = PythonHttpAnalysisHandler(
                PythonHttpAnalysisHandlerConfig(
                    endpoint = URI.create("http://127.0.0.1:${server.address.port}/analyze"),
                    bearerToken = "worker-secret"
                )
            )
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
                createdAt = Instant.parse("2026-08-10T12:00:00Z")
            )

            val output = handler.analyze(
                AnalysisHandlerInput(
                    listOf(AnalysisDataFileInput(source, ByteArrayInputStream("x,y\n1,2".toByteArray()))),
                    mapOf("model" to "demo")
                )
            )

            output.use {
                assertEquals("prediction.json", it.fileName)
                assertEquals("application/json", it.mediaType)
                assertEquals("{\"prediction\":\"ok\"}", it.content.bufferedReader().readText())
                assertEquals(
                    DataFileMetadataValue.NumberValue("0.95".toBigDecimal()),
                    it.metadata.values["score"]
                )
            }
            assertEquals("Bearer worker-secret", authorization)
            assertTrue(requestBody.contains("\"schemaVersion\":1"))
            assertTrue(requestBody.contains("\"dataFileId\":42"))
            assertTrue(requestBody.contains("x,y\n1,2"))
        } finally {
            server.stop(0)
        }
    }
}
