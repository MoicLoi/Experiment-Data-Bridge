package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.analysis.AnalysisParameterContract
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandler
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandlerInput
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandlerOutput
import me.moiclathy.edb.core.datafile.analysis.AnalysisTaskApplicationService
import me.moiclathy.edb.core.datafile.analysis.AnalysisTaskHandler
import me.moiclathy.edb.core.datafile.analysis.SubmitAnalysisTaskCommand
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisRoutesTest {
    @Test
    fun `lists handlers submits analysis and lists generated artifacts`() = testApplication {
        val service = mockk<AnalysisTaskApplicationService>()
        val command = slot<SubmitAnalysisTaskCommand>()
        every { service.listHandlers(7, 11) } returns listOf(handler())
        coEvery { service.submit(capture(command)) } returns queuedTask()
        every { service.listArtifactsBySource(7, 42, 11) } returns listOf(artifact())

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { analysisRoutes(service) }
        }

        val handlers = client.get("/api/v1/projects/7/analysis-handlers") { bearerAuth(token()) }
        val submitted = client.post("/api/v1/projects/7/analyses") {
            bearerAuth(token())
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"handlerId":"python-http","sourceDataFileIds":[42],"parameterValues":{"method":"demo"},"priority":75}""")
        }
        val artifacts = client.get("/api/v1/projects/7/data-files/42/analyses") { bearerAuth(token()) }

        assertEquals(HttpStatusCode.OK, handlers.status)
        assertEquals(HttpStatusCode.Accepted, submitted.status)
        assertEquals(HttpStatusCode.OK, artifacts.status)
        assertTrue(handlers.bodyAsText().contains("python-http"))
        assertTrue(handlers.bodyAsText().contains("parameterContract"))
        assertTrue(artifacts.bodyAsText().contains("prediction.json"))
        assertEquals(setOf(42), command.captured.sourceDataFileIds)
        assertEquals("demo", command.captured.parameterValues?.get("method")?.toString()?.trim('"'))
        assertEquals(75, command.captured.priority.value)
    }

    private fun handler() = object : AnalysisHandler {
        override val id = "python-http"
        override val analysisType = "python"
        override val tool = "python-http"
        override val outputFormat = DataFileFormat.JSON
        override val parameterContract = AnalysisParameterContract(
            id = "demo", version = 1, title = "Demo parameters",
            schema = buildJsonObject { put("type", "object") }
        )
        override suspend fun analyze(input: AnalysisHandlerInput): AnalysisHandlerOutput = error("not called")
    }

    private fun queuedTask() = Task.pending(
        projectId = 7,
        taskType = AnalysisTaskHandler.TASK_TYPE,
        configSnapshot = null,
        triggerUserId = 11,
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    ).copy(id = 82, status = TaskStatus.QUEUED)

    private fun artifact() = DataFile(
        id = 44,
        projectId = 7,
        createdByUserId = 11,
        kind = DataFileKind.ANALYSIS_RESULT,
        format = DataFileFormat.JSON,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "prediction.json",
        provenance = DataFileProvenance(DataFileOrigin.ANALYSIS, setOf(42), 82, "python-http"),
        createdAt = Instant.parse("2026-08-10T12:01:00Z")
    )

    private fun token(): String = JWT.create()
        .withSubject("researcher")
        .withClaim("userId", 11)
        .sign(ALGORITHM)

    companion object {
        private val ALGORITHM = Algorithm.HMAC256("analysis-routes-test-secret")
    }
}
