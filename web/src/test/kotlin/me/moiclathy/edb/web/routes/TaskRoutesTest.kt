package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.application.SubmitPipelineTaskCommand
import me.moiclathy.edb.core.task.application.TaskApplicationService
import me.moiclathy.edb.core.task.application.TaskListQuery
import me.moiclathy.edb.core.task.application.TaskPage
import me.moiclathy.edb.core.task.audit.TaskAuditEvent
import me.moiclathy.edb.core.task.audit.TaskAuditEventType
import me.moiclathy.edb.core.task.pipeline.PipelineTaskHandler
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class TaskRoutesTest {
    @Test
    fun `authenticated task endpoints submit query and cancel`() = testApplication {
        val service = mockk<TaskApplicationService>()
        val command = slot<SubmitPipelineTaskCommand>()
        val queued = task(TaskStatus.QUEUED)
        val cancelled = task(TaskStatus.CANCELLED)
        coEvery { service.submitPipelineTask(capture(command)) } returns queued
        every { service.getTask(51, 11) } returns queued
        coEvery { service.requestCancellation(51, 11) } returns cancelled
        every {
            service.listTasks(TaskListQuery(7, TaskStatus.QUEUED, 0, 20, 11))
        } returns TaskPage(listOf(queued), 0, 20, 1, 1)
        every { service.getTaskTimeline(51, 11) } returns listOf(
            TaskAuditEvent(
                id = 1,
                taskId = 51,
                type = TaskAuditEventType.CREATED,
                fromStatus = null,
                toStatus = TaskStatus.PENDING,
                actorUserId = 11,
                createdAt = Instant.parse("2026-08-10T12:00:00Z")
            )
        )

        application {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { taskRoutes(service) }
        }

        val submitted = client.post("/api/v1/tasks/pipeline") {
            bearerAuth(token())
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"projectId":7,"pipelineId":"import-data","context":{"source":"lab.csv"},"priority":75}"""
            )
        }
        val queried = client.get("/api/v1/tasks/51") { bearerAuth(token()) }
        val cancellation = client.post("/api/v1/tasks/51/cancel") { bearerAuth(token()) }
        val listed = client.get("/api/v1/projects/7/tasks?status=QUEUED&page=0&size=20") {
            bearerAuth(token())
        }
        val timeline = client.get("/api/v1/tasks/51/timeline") { bearerAuth(token()) }

        assertEquals(HttpStatusCode.Created, submitted.status)
        assertEquals(HttpStatusCode.OK, queried.status)
        assertEquals(HttpStatusCode.OK, cancellation.status)
        assertEquals(HttpStatusCode.OK, listed.status)
        assertEquals(HttpStatusCode.OK, timeline.status)
        assertEquals(11, command.captured.requestedBy)
        assertEquals("import-data", command.captured.pipelineId)
    }

    private fun task(status: TaskStatus) = Task.pending(
        projectId = 7,
        taskType = PipelineTaskHandler.TASK_TYPE,
        configSnapshot = null,
        triggerUserId = 11,
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    ).copy(
        id = 51,
        status = status,
        completedAt = if (status.isTerminal) Instant.parse("2026-08-10T12:01:00Z") else null,
        updatedAt = if (status.isTerminal) Instant.parse("2026-08-10T12:01:00Z")
        else Instant.parse("2026-08-10T12:00:00Z")
    )

    private fun token(): String = JWT.create()
        .withSubject("researcher")
        .withClaim("userId", 11)
        .sign(ALGORITHM)

    companion object {
        private val ALGORITHM = Algorithm.HMAC256("task-routes-test-secret")
    }
}
