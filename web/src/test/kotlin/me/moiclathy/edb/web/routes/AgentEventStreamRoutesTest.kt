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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.application.AgentPlanningApplicationService
import me.moiclathy.edb.agent.event.AgentLifecycleEvent
import me.moiclathy.edb.agent.model.AgentRunStatus
import me.moiclathy.edb.agent.model.AgentRun
import me.moiclathy.edb.agent.model.AgentSession
import me.moiclathy.edb.agent.model.AgentSessionStatus
import me.moiclathy.edb.agent.persistence.AgentAuditEventType
import me.moiclathy.edb.core.event.CoroutineEventBus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentEventStreamRoutesTest {
    @Test
    fun `streams only authorized session Agent lifecycle events`() = testApplication {
        val service = mockk<AgentPlanningApplicationService>()
        val eventBus = CoroutineEventBus()
        every { service.getSession(SESSION_ID, USER_ID) } returns AgentSession(
            SESSION_ID, USER_ID, PROJECT_ID, "workflow-planner", "1", AgentSessionStatus.ACTIVE, NOW, NOW
        )
        every { service.getSessionRuns(SESSION_ID, USER_ID) } returns listOf(
            AgentRun("run-snapshot", SESSION_ID, "correlation", AgentRunStatus.PLANNING, "fake", NOW, NOW)
        )
        application {
            install(Authentication) {
                jwt("auth-jwt") { verifier(JWT.require(ALGORITHM).build()); validate { JWTPrincipal(it.payload) } }
            }
            routing { agentEventStreamRoutes(service, eventBus) }
        }

        try {
            client.prepareGet("/api/v1/agent-sessions/$SESSION_ID/events") { bearerAuth(token()) }.execute { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsChannel()
                assertEquals(": connected", withTimeout(2_000) { body.readUTF8Line() })
                assertEquals("", withTimeout(2_000) { body.readUTF8Line() })
                assertTrue(withTimeout(2_000) { body.readUTF8Line() }.orEmpty().startsWith("id: run-snapshot-"))
                assertEquals("event: run_snapshot", withTimeout(2_000) { body.readUTF8Line() })
                assertTrue(withTimeout(2_000) { body.readUTF8Line() }.orEmpty().contains("PLANNING"))
                assertEquals("", withTimeout(2_000) { body.readUTF8Line() })
                eventBus.publish(
                    AgentLifecycleEvent(
                        SESSION_ID, "run-1", USER_ID, PROJECT_ID, AgentAuditEventType.VALIDATION_FAILED,
                        AgentRunStatus.VALIDATING, buildJsonObject { put("errorCount", 1) }, NOW
                    )
                )
                assertTrue(withTimeout(2_000) { body.readUTF8Line() }.orEmpty().startsWith("id: run-1-"))
                assertEquals("event: validation_failed", withTimeout(2_000) { body.readUTF8Line() })
                assertTrue(withTimeout(2_000) { body.readUTF8Line() }.orEmpty().contains("errorCount"))
            }
        } finally {
            eventBus.close()
        }
    }

    private fun token() = JWT.create().withSubject("scientist").withClaim("userId", USER_ID).sign(ALGORITHM)

    companion object {
        private const val USER_ID = 7
        private const val PROJECT_ID = 11
        private const val SESSION_ID = "session-1"
        private val NOW = Instant.parse("2026-08-11T16:00:00Z")
        private val ALGORITHM = Algorithm.HMAC256("agent-sse-test-secret")
    }
}
