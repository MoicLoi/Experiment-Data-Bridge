package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readUTF8Line
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.withTimeout
import me.moiclathy.edb.core.event.CoroutineEventBus
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.application.TaskApplicationService
import me.moiclathy.edb.core.task.event.TaskLifecycleEvent
import me.moiclathy.edb.core.task.event.TaskLifecycleEventType
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskEventStreamRoutesTest {
    @Test
    fun `streams project lifecycle events as authenticated SSE`() = testApplication {
        val service = mockk<TaskApplicationService>()
        val eventBus = CoroutineEventBus()
        every { service.ensureProjectReadable(7, 11) } returns Unit

        application {
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { taskEventStreamRoutes(service, eventBus) }
        }

        try {
            client.prepareGet("/api/v1/projects/7/tasks/events") {
                bearerAuth(token())
            }.execute { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsChannel()
                assertEquals(": connected", withTimeout(2_000) { body.readUTF8Line() })
                assertEquals("", withTimeout(2_000) { body.readUTF8Line() })

                eventBus.publish(
                    TaskLifecycleEvent(
                        taskId = 81,
                        projectId = 7,
                        type = TaskLifecycleEventType.PROGRESS_UPDATED,
                        previousStatus = TaskStatus.RUNNING,
                        status = TaskStatus.RUNNING,
                        attemptCount = 1,
                        details = mapOf("progressPercent" to "40"),
                        occurredAt = Instant.parse("2026-08-10T12:00:00Z")
                    )
                )

                val id = withTimeout(2_000) { body.readUTF8Line() }.orEmpty()
                val type = withTimeout(2_000) { body.readUTF8Line() }.orEmpty()
                val data = withTimeout(2_000) { body.readUTF8Line() }.orEmpty()
                assertTrue(id.startsWith("id: 81-"))
                assertEquals("event: progress_updated", type)
                assertTrue(data.contains("\"progressPercent\":\"40\""))
            }
        } finally {
            eventBus.close()
        }
    }

    private fun token(): String = JWT.create()
        .withSubject("researcher")
        .withClaim("userId", 11)
        .sign(ALGORITHM)

    companion object {
        private val ALGORITHM = Algorithm.HMAC256("task-sse-test-secret")
    }
}
