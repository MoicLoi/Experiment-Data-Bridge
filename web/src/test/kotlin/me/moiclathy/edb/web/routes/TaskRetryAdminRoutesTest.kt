package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
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
import io.mockk.mockk
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.application.TaskRetryAdminService
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class TaskRetryAdminRoutesTest {
    @Test
    fun `admin requeues failed task while member is forbidden`() = testApplication {
        val service = mockk<TaskRetryAdminService>()
        val requeued = Task(
            id = 91,
            projectId = 7,
            taskType = "pipeline",
            status = TaskStatus.QUEUED,
            triggerUserId = 11,
            createdAt = Instant.parse("2026-08-10T14:00:00Z"),
            updatedAt = Instant.parse("2026-08-10T15:00:00Z")
        )
        coEvery { service.requeue(91, 12) } returns requeued

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { taskRetryAdminRoutes(service) }
        }

        val admin = client.post(PATH) { bearerAuth(token(UserRole.ADMIN)) }
        val member = client.post(PATH) { bearerAuth(token(UserRole.MEMBER)) }

        assertEquals(HttpStatusCode.OK, admin.status)
        assertEquals(HttpStatusCode.Forbidden, member.status)
    }

    private fun token(role: UserRole): String = JWT.create()
        .withSubject("operator")
        .withClaim("userId", 12)
        .withClaim("role", role.name)
        .sign(ALGORITHM)

    companion object {
        private const val PATH = "/api/v1/admin/tasks/91/requeue"
        private val ALGORITHM = Algorithm.HMAC256("task-requeue-test-secret")
    }
}
