package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AgentApiClientTest {
    @Test
    fun `maps the user Agent lifecycle to the frozen HTTP contract`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val transport = TaskHttpTransport { _, request ->
            requests += request
            TaskHttpResponse(if (request.path.endsWith("agent-sessions")) 201 else 200, "{}")
        }
        val client = AgentApiClient(TaskServerConfig("http://localhost:25560", "token"), transport)

        client.profiles(); client.quota(7); client.createSession(7, "workflow-planner", "1")
        client.session("session-1"); client.messages("session-1"); client.send("session-1", "Plan safely", 2)
        client.run("run-1"); client.timeline("run-1"); client.proposals("run-1"); client.cancelRun("run-1")
        client.proposal("proposal-1"); client.approve("proposal-1"); client.reject("proposal-1", "Not suitable")
        client.decision("proposal-1"); client.submit("proposal-1", mapOf("source" to "data.csv"))

        assertEquals("/api/v1/agent-model-profiles", requests[0].path)
        assertEquals("/api/v1/projects/7/agent-token-quota", requests[1].path)
        assertEquals("/api/v1/projects/7/agent-sessions", requests[2].path)
        assertEquals("/api/v1/agent-sessions/session-1/messages", requests[5].path)
        assertEquals("/api/v1/agent-runs/run-1/audit", requests[7].path)
        assertEquals("/api/v1/agent-proposals/proposal-1/submit", requests.last().path)
        val submit = Json.parseToJsonElement(requireNotNull(requests.last().body)).jsonObject
        assertEquals("data.csv", submit.getValue("context").jsonObject.getValue("source").jsonPrimitive.content)
    }

    @Test
    fun `maps Agent administration and handles an absent retention result`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val transport = TaskHttpTransport { _, request ->
            requests += request
            if (request.path.endsWith("/last-result")) TaskHttpResponse(204, "") else TaskHttpResponse(200, "{}")
        }
        val client = AgentApiClient(TaskServerConfig("http://localhost:25560", "admin-token"), transport)

        client.providerStatuses(); client.modelMetrics(); client.metrics(); client.retentionOverview()
        client.retentionEstimate(); client.retentionCleanup()
        val last = client.retentionLastResult()

        assertEquals(
            listOf(
                "/api/v1/admin/agent-providers",
                "/api/v1/admin/agent-model-metrics",
                "/api/v1/admin/agent-metrics",
                "/api/v1/admin/agent-retention",
                "/api/v1/admin/agent-retention/estimate",
                "/api/v1/admin/agent-retention/cleanup",
                "/api/v1/admin/agent-retention/last-result"
            ),
            requests.map { it.path }
        )
        assertEquals("POST", requests[5].method)
        assertEquals(kotlinx.serialization.json.JsonNull, last)
    }
}
