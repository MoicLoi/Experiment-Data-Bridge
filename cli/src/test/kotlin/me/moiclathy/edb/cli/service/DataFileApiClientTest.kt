package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataFileApiClientTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `maps file operations and task submissions to datafile APIs`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(
            listOf(
                TaskHttpResponse(200, "[${dataFileJson()}]"),
                TaskHttpResponse(200, dataFileJson()),
                TaskHttpResponse(202, taskJson("datafile-conversion")),
                TaskHttpResponse(202, taskJson("datafile-analysis")),
                TaskHttpResponse(202, taskJson("datafile-report")),
                TaskHttpResponse(200, conversionTargetsJson()),
                TaskHttpResponse(200, "[${dataFileJson()}]"),
                TaskHttpResponse(200, analysisHandlersJson()),
                TaskHttpResponse(200, "[${dataFileJson()}]"),
                TaskHttpResponse(200, reportDefinitionsJson()),
                TaskHttpResponse(200, "[${dataFileJson()}]")
            )
        )
        val binary = FakeBinaryTransport()
        val client = DataFileApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, request -> requests.add(request).let { responses.removeFirst() } },
            binary
        )
        val uploadFile = Files.writeString(directory.resolve("lab.csv"), "x,y\n1,2\n")

        assertEquals(81, client.upload(7, uploadFile).id)
        assertEquals(1, client.list(7).size)
        assertEquals("lab.csv", client.get(7, 81).originalFileName)
        client.submitConversion(7, 81, "json", mapOf("pretty" to "true"), execution())
        client.submitAnalysis(7, "python.summary", setOf(81, 82), mapOf("mode" to "fast"), execution())
        client.submitReport(7, "summary", setOf(81), emptyMap(), execution())
        assertEquals("JSON", client.conversionTargets(7, 81).single().targetFormat)
        assertEquals(1, client.conversionArtifacts(7, 81).size)
        assertEquals("python.summary", client.analysisHandlers(7).single().id)
        assertEquals(1, client.analysisArtifacts(7, 81).size)
        assertEquals("summary", client.reportDefinitions(7).single().id)
        assertEquals(1, client.reportArtifacts(7, 81).size)
        assertEquals(10, client.download(7, 81, directory.resolve("copy.csv")).bytesWritten)

        assertEquals(
            listOf(
                "/api/v1/projects/7/data-files",
                "/api/v1/projects/7/data-files/81",
                "/api/v1/projects/7/data-files/81/conversions",
                "/api/v1/projects/7/analyses",
                "/api/v1/projects/7/reports",
                "/api/v1/projects/7/data-files/81/conversion-targets",
                "/api/v1/projects/7/data-files/81/conversions",
                "/api/v1/projects/7/analysis-handlers",
                "/api/v1/projects/7/data-files/81/analyses",
                "/api/v1/projects/7/reports/definitions",
                "/api/v1/projects/7/data-files/81/reports"
            ),
            requests.map { it.path }
        )
        val conversion = Json.parseToJsonElement(requireNotNull(requests[2].body)).jsonObject
        assertEquals("JSON", conversion.getValue("targetFormat").jsonPrimitive.content)
        val analysis = Json.parseToJsonElement(requireNotNull(requests[3].body)).jsonObject
        assertEquals(listOf("81", "82"), analysis.getValue("sourceDataFileIds").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(uploadFile, binary.uploadedFile)
    }

    @Test
    fun `jdk download refuses to overwrite before opening a connection`() {
        val existing = Files.writeString(directory.resolve("existing.csv"), "keep")
        val transport = JdkDataFileBinaryTransport()

        assertFailsWith<IllegalArgumentException> {
            transport.download(TaskServerConfig("http://127.0.0.1:1", "token"), 7, 81, existing, force = false)
        }
        assertEquals("keep", Files.readString(existing))
    }

    private fun execution() = TaskSubmissionCliOptions(priority = 75, maxAttempts = 3, timeoutMs = 60_000)

    private inner class FakeBinaryTransport : DataFileBinaryTransport {
        var uploadedFile: Path? = null

        override fun upload(config: TaskServerConfig, projectId: Int, file: Path, mediaType: String?) =
            TaskHttpResponse(201, dataFileJson()).also { uploadedFile = file }

        override fun download(
            config: TaskServerConfig,
            projectId: Int,
            dataFileId: Int,
            target: Path,
            force: Boolean
        ) = DataFileDownloadResult(target, 10, "text/csv")
    }

    private fun dataFileJson() =
        """
        {
          "id":81,"projectId":7,"createdByUserId":11,"kind":"SOURCE","format":"CSV","status":"AVAILABLE",
          "originalFileName":"lab.csv","mediaType":"text/csv","sizeBytes":10,"sha256":"abc",
          "storageProvider":"local","origin":"UPLOAD","parentDataFileIds":[],"producingTaskId":null,
          "externalSystem":null,"metadata":{"rows":1},"createdAt":"2026-08-11T10:00:00Z",
          "updatedAt":"2026-08-11T10:00:00Z"
        }
        """.trimIndent()

    private fun taskJson(type: String) =
        """
        {
          "id":51,"projectId":7,"taskType":"$type","status":"QUEUED","priority":75,
          "attemptCount":0,"maxAttempts":3,"nextAttemptAt":null,"timeoutMs":60000,
          "cancellationRequestedAt":null,"cancellationRequestedBy":null,"progress":null,"result":null,
          "error":null,"createdAt":"2026-08-11T10:00:00Z","updatedAt":"2026-08-11T10:00:00Z",
          "startedAt":null,"completedAt":null
        }
        """.trimIndent()

    private fun conversionTargetsJson() =
        """[{"sourceFormat":"CSV","targetFormat":"JSON","options":[{"name":"pretty","type":"BOOLEAN","required":false,"defaultValue":"false","description":"Pretty print JSON"}]}]"""

    private fun analysisHandlersJson() =
        """[{"id":"python.summary","analysisType":"summary","tool":"python","outputFormat":"JSON"}]"""

    private fun reportDefinitionsJson() =
        """[{"id":"summary","version":1,"title":"Summary","generatorId":"tabular-summary","outputFormat":"XLSX","outputFileName":"summary.xlsx","defaultParameters":{"title":"Summary"}}]"""
}
