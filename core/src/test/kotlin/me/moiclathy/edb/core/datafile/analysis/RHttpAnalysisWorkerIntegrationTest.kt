package me.moiclathy.edb.core.datafile.analysis

import kotlinx.coroutines.runBlocking
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
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RHttpAnalysisWorkerIntegrationTest {
    @Test
    fun `Kotlin handler executes R statistical summary over HTTP`() = runBlocking {
        assumeTrue(System.getenv("EDB_R_WORKER_INTEGRATION") == "true", "R worker integration test is opt-in.")
        val endpoint = System.getenv("EDB_R_WORKER_URL") ?: "http://127.0.0.1:18001/analyze"
        val handler = ExternalHttpAnalysisHandler(ExternalHttpAnalysisHandlerConfig(
            id = "r-statistical-summary",
            analysisType = "statistics",
            tool = "r-http",
            endpoint = URI.create(endpoint),
            outputFormat = DataFileFormat.JSON,
            bearerToken = System.getenv("EDB_R_WORKER_TOKEN")
        ))
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

        handler.analyze(AnalysisHandlerInput(
            sources = listOf(AnalysisDataFileInput(
                source,
                ByteArrayInputStream("x,y,label\n1,2,a\n3,,b\n".toByteArray())
            )),
            parameters = emptyMap()
        )).use { output ->
            assertEquals("r-statistical-summary.json", output.fileName)
            assertEquals("application/json", output.mediaType)
            val body = output.content.bufferedReader().readText()
            assertTrue(body.contains("\"rows\": 2"))
            assertTrue(body.contains("\"columns\": 3"))
        }
    }
}
