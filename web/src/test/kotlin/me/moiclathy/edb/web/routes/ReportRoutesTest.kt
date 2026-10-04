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
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.report.ReportDefinition
import me.moiclathy.edb.core.datafile.report.ReportTaskApplicationService
import me.moiclathy.edb.core.datafile.report.ReportTaskHandler
import me.moiclathy.edb.core.datafile.report.SubmitReportTaskCommand
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportRoutesTest {
    @Test
    fun `lists definitions submits report task and lists source artifacts`() = testApplication {
        val service = mockk<ReportTaskApplicationService>()
        val command = slot<SubmitReportTaskCommand>()
        every { service.listDefinitions(7, 11) } returns listOf(definition())
        coEvery { service.submit(capture(command)) } returns queuedTask()
        every { service.listArtifactsBySource(7, 42, 11) } returns listOf(reportArtifact())

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { reportRoutes(service) }
        }

        val definitions = client.get("/api/v1/projects/7/reports/definitions") { bearerAuth(token()) }
        val submitted = client.post("/api/v1/projects/7/reports") {
            bearerAuth(token())
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"definitionId":"experiment-summary","sourceDataFileIds":[42],"parameters":{"sampleRows":"3"},"priority":75}"""
            )
        }
        val artifacts = client.get("/api/v1/projects/7/data-files/42/reports") { bearerAuth(token()) }

        assertEquals(HttpStatusCode.OK, definitions.status)
        assertEquals(HttpStatusCode.Accepted, submitted.status)
        assertEquals(HttpStatusCode.OK, artifacts.status)
        assertTrue(definitions.bodyAsText().contains("experiment-summary"))
        assertTrue(artifacts.bodyAsText().contains("summary.pdf"))
        assertEquals(setOf(42), command.captured.sourceDataFileIds)
        assertEquals(11, command.captured.requestedBy)
        assertEquals(75, command.captured.priority.value)
    }

    private fun definition() = ReportDefinition(
        id = "experiment-summary",
        title = "Experiment Summary",
        generatorId = "tabular-summary",
        outputFormat = DataFileFormat.PDF,
        outputFileName = "summary.pdf"
    )

    private fun queuedTask() = Task.pending(
        projectId = 7,
        taskType = ReportTaskHandler.TASK_TYPE,
        configSnapshot = null,
        triggerUserId = 11,
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    ).copy(id = 81, status = TaskStatus.QUEUED)

    private fun reportArtifact() = DataFile(
        id = 43,
        projectId = 7,
        createdByUserId = 11,
        kind = DataFileKind.REPORT,
        format = DataFileFormat.PDF,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "summary.pdf",
        provenance = DataFileProvenance(DataFileOrigin.REPORT_GENERATION, setOf(42), 81),
        createdAt = Instant.parse("2026-08-10T12:01:00Z")
    )

    private fun token(): String = JWT.create()
        .withSubject("researcher")
        .withClaim("userId", 11)
        .sign(ALGORITHM)

    companion object {
        private val ALGORITHM = Algorithm.HMAC256("report-routes-test-secret")
    }
}
