package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.retention.TaskCleanupResult
import me.moiclathy.edb.core.task.retention.TaskRetentionAdminService
import me.moiclathy.edb.core.task.retention.TaskRetentionEstimate
import me.moiclathy.edb.core.task.retention.TaskRetentionManagerState
import me.moiclathy.edb.core.task.retention.TaskRetentionPolicy
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class TaskRetentionAdminRoutesTest {
    @Test
    fun `admin accesses retention management endpoints while member is forbidden`() = testApplication {
        val service = mockk<TaskRetentionAdminService>()
        val now = Instant.parse("2026-08-10T17:00:00Z")
        val cleanup = TaskCleanupResult(now, 12, 2, now)
        every { service.policy } returns TaskRetentionPolicy(enabled = true)
        every { service.state() } returns TaskRetentionManagerState(lastResult = cleanup)
        coEvery { service.estimate() } returns TaskRetentionEstimate(now, 12, now)
        coEvery { service.cleanup() } returns cleanup

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { taskRetentionAdminRoutes(service) }
        }

        val overview = client.get(PATH) { bearerAuth(token(UserRole.ADMIN)) }
        val estimate = client.get("$PATH/estimate") { bearerAuth(token(UserRole.ADMIN)) }
        val cleanupResponse = client.post("$PATH/cleanup") { bearerAuth(token(UserRole.ADMIN)) }
        val latest = client.get("$PATH/last-result") { bearerAuth(token(UserRole.ADMIN)) }
        val forbidden = client.get(PATH) { bearerAuth(token(UserRole.MEMBER)) }

        assertEquals(HttpStatusCode.OK, overview.status)
        assertEquals(HttpStatusCode.OK, estimate.status)
        assertEquals(HttpStatusCode.OK, cleanupResponse.status)
        assertEquals(HttpStatusCode.OK, latest.status)
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
    }

    private fun token(role: UserRole): String = JWT.create()
        .withSubject("operator")
        .withClaim("userId", 12)
        .withClaim("role", role.name)
        .sign(ALGORITHM)

    companion object {
        private const val PATH = "/api/v1/admin/task-retention"
        private val ALGORITHM = Algorithm.HMAC256("task-retention-test-secret")
    }
}
