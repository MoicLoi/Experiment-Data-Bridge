package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.metrics.TaskMetricsApplicationService
import me.moiclathy.edb.core.task.metrics.TaskRuntimeMetrics
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class TaskMetricsAdminRoutesTest {
    @Test
    fun `admin reads task metrics while member is forbidden`() = testApplication {
        val service = mockk<TaskMetricsApplicationService>()
        every { service.snapshot() } returns TaskRuntimeMetrics(
            queueDepth = 3,
            queuedCount = 2,
            retryingCount = 1,
            runningCount = 4,
            succeededCount = 20,
            failedCount = 2,
            timedOutCount = 1,
            averageExecutionDurationMs = 950.0,
            collectedAt = Instant.parse("2026-08-10T14:00:00Z")
        )

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { taskMetricsAdminRoutes(service) }
        }

        val admin = client.get(PATH) { bearerAuth(token(UserRole.ADMIN)) }
        val member = client.get(PATH) { bearerAuth(token(UserRole.MEMBER)) }

        assertEquals(HttpStatusCode.OK, admin.status)
        assertEquals(HttpStatusCode.Forbidden, member.status)
    }

    private fun token(role: UserRole): String = JWT.create()
        .withSubject("operator")
        .withClaim("userId", 11)
        .withClaim("role", role.name)
        .sign(ALGORITHM)

    companion object {
        private const val PATH = "/api/v1/admin/task-metrics"
        private val ALGORITHM = Algorithm.HMAC256("task-metrics-test-secret")
    }
}
