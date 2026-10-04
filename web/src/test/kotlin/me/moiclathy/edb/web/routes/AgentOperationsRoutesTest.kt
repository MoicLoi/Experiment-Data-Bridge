package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
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
import me.moiclathy.edb.agent.application.AgentOperationsService
import me.moiclathy.edb.agent.application.AgentRetentionPolicy
import me.moiclathy.edb.agent.application.AgentRetentionManager
import me.moiclathy.edb.agent.persistence.AgentOperationsRepository
import me.moiclathy.edb.agent.persistence.AgentRunMetrics
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentOperationsRoutesTest {
    @Test
    fun `admin reads metrics estimates and runs bounded retention cleanup`() = testApplication {
        val repository = mockk<AgentOperationsRepository>()
        every { repository.metrics() } returns AgentRunMetrics(8, 1, 1, 1, 0, 0, 0, 3, 1, 1, 120, 40, 250.0)
        every { repository.countExpiredRuns(any()) } returns 3
        every { repository.deleteExpiredRuns(any(), 2) } returnsMany listOf(2, 1)
        val service = AgentOperationsService(
            repository, AgentRetentionPolicy(Duration.ofDays(30), batchSize = 2, maxBatches = 3),
            Clock.fixed(NOW, ZoneOffset.UTC)
        )
        val manager = AgentRetentionManager(service, service.retentionPolicy, clock = Clock.fixed(NOW, ZoneOffset.UTC))
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) {
                jwt("auth-jwt") { verifier(JWT.require(ALGORITHM).build()); validate { JWTPrincipal(it.payload) } }
            }
            routing { agentOperationsAdminRoutes(service, manager) }
        }

        val metrics = client.get("/api/v1/admin/agent-metrics") { bearerAuth(token("ADMIN")) }
        val estimate = client.get("/api/v1/admin/agent-retention/estimate") { bearerAuth(token("ADMIN")) }
        val cleanup = client.post("/api/v1/admin/agent-retention/cleanup") { bearerAuth(token("ADMIN")) }
        val last = client.get("/api/v1/admin/agent-retention/last-result") { bearerAuth(token("ADMIN")) }
        val forbidden = client.get("/api/v1/admin/agent-metrics") { bearerAuth(token("MEMBER")) }

        assertEquals(HttpStatusCode.OK, metrics.status)
        assertTrue(metrics.bodyAsText().contains("\"totalRuns\":8"))
        assertTrue(estimate.bodyAsText().contains("\"expiredRuns\":3"))
        assertTrue(cleanup.bodyAsText().contains("\"deletedRuns\":3"))
        assertTrue(last.bodyAsText().contains("\"batches\":2"))
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
    }

    private fun token(role: String) = JWT.create().withSubject("operator").withClaim("userId", 7)
        .withClaim("role", role).sign(ALGORITHM)

    companion object {
        private val NOW = Instant.parse("2026-08-11T16:00:00Z")
        private val ALGORITHM = Algorithm.HMAC256("agent-operations-test-secret")
    }
}
