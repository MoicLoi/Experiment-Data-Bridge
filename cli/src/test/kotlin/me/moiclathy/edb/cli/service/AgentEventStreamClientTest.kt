package me.moiclathy.edb.cli.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AgentEventStreamClientTest {
    @Test
    fun `filters a session stream by run and stops on terminal snapshot`() {
        var requestedPath: String? = null
        val transport = TaskEventStreamTransport { _, path, onLine ->
            requestedPath = path
            listOf(
                ": connected", "",
                "id: other-1", "event: model_delta", "data: ${event("run-other", "PLANNING", "MODEL_TEXT_DELTA")}", "",
                "id: target-1", "event: run_snapshot", "data: ${event("run-1", "COMPLETED", "RUN_SNAPSHOT")}", ""
            ).forEach(onLine)
        }
        val events = mutableListOf<AgentLifecycleCliEvent>()
        val client = AgentEventStreamClient(TaskServerConfig("http://localhost:25560", "token"), transport)

        val reason = client.watch("session-1", "run-1", onEvent = events::add)

        assertEquals("/api/v1/agent-sessions/session-1/events", requestedPath)
        assertEquals(listOf("RUN_SNAPSHOT"), events.map { it.type })
        assertEquals(AgentWatchStopReason.TERMINAL, reason)
    }

    private fun event(runId: String, status: String, type: String) =
        """{"sessionId":"session-1","runId":"$runId","projectId":7,"type":"$type","runStatus":"$status","detailsJson":"{}","occurredAt":"2026-08-11T10:00:00Z"}"""
}
