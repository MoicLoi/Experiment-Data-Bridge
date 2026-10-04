package me.moiclathy.edb.web.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentApiDocumentationRoutesTest {
    @Test
    fun `serves parseable Agent OpenAPI and documentation`() = testApplication {
        application { routing { agentApiDocumentationRoutes() } }
        val contract = client.get("/api/v1/openapi/agent-0.25.yaml")
        val contractText = contract.bodyAsText()
        val document = Load(LoadSettings.builder().build()).loadFromString(contractText) as Map<*, *>
        val paths = document["paths"] as Map<*, *>
        val docs = client.get("/api/v1/docs/agent-0.25")

        assertEquals(HttpStatusCode.OK, contract.status)
        assertEquals(24, paths.size)
        assertTrue(paths.containsKey("/projects/{projectId}/agent-token-quota"))
        assertTrue(paths.containsKey("/admin/agent-providers"))
        assertTrue(paths.containsKey("/admin/agent-model-metrics"))
        assertTrue(paths.containsKey("/admin/agent-metrics"))
        assertTrue(paths.containsKey("/admin/agent-retention/cleanup"))
        assertTrue(paths.containsKey("/agent-model-profiles"))
        assertTrue(paths.containsKey("/agent-sessions/{sessionId}/events"))
        assertTrue(paths.containsKey("/agent-runs/{runId}/audit"))
        assertTrue(paths.containsKey("/agent-proposals/{proposalId}/approve"))
        assertTrue(paths.containsKey("/agent-proposals/{proposalId}/submit"))
        assertTrue(paths.containsKey("/agent-runs/{runId}/cancel"))
        assertEquals(HttpStatusCode.OK, docs.status)
        assertTrue(docs.bodyAsText().contains("Agent API 0.25"))
    }
}
