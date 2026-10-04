package me.moiclathy.edb.web.routes

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.auth.authenticate
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.moiclathy.edb.agent.application.AgentPlanningApplicationService
import me.moiclathy.edb.agent.event.AgentLifecycleEvent
import me.moiclathy.edb.agent.persistence.AgentAuditEventType
import me.moiclathy.edb.core.event.EventBus
import me.moiclathy.edb.core.event.subscribe
import me.moiclathy.edb.web.dto.AgentLifecycleStreamEvent
import me.moiclathy.edb.web.security.requireUserId

fun Route.agentEventStreamRoutes(service: AgentPlanningApplicationService, eventBus: EventBus) {
    authenticate("auth-jwt") {
        get("/api/v1/agent-sessions/{sessionId}/events") {
            val userId = call.requireUserId() ?: return@get
            val sessionId = call.parameters["sessionId"]?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Agent session ID is required.")
            val session = service.getSession(sessionId, userId)
            call.response.cacheControl(CacheControl.NoCache(null))
            call.response.header(HttpHeaders.Connection, "keep-alive")
            call.respondTextWriter(ContentType.Text.EventStream) {
                val events = Channel<AgentLifecycleEvent>(64, BufferOverflow.DROP_OLDEST)
                val subscription = eventBus.subscribe<AgentLifecycleEvent> { event ->
                    if (event.sessionId == sessionId) events.trySend(event)
                }
                var streamSequence = 0L
                try {
                    write(": connected\n\n"); flush()
                    service.getSessionRuns(sessionId, userId).forEach { run ->
                        events.trySend(AgentLifecycleEvent(
                            sessionId = sessionId,
                            runId = run.id,
                            actorUserId = userId,
                            projectId = session.projectId,
                            type = AgentAuditEventType.RUN_SNAPSHOT,
                            runStatus = run.status,
                            occurredAt = run.updatedAt
                        ))
                    }
                    while (currentCoroutineContext().isActive) {
                        val event = withTimeoutOrNull(15_000) { events.receive() }
                        if (event == null) {
                            write(": heartbeat\n\n")
                        } else {
                            streamSequence++
                            write("id: ${event.runId ?: event.sessionId}-${event.occurredAt.toEpochMilli()}-$streamSequence\n")
                            write("event: ${event.type.name.lowercase()}\n")
                            write("data: ${AGENT_SSE_JSON.encodeToString(event.toResponse())}\n\n")
                        }
                        flush()
                    }
                } finally {
                    subscription.cancel(); events.close()
                }
            }
        }
    }
}

private fun AgentLifecycleEvent.toResponse() = AgentLifecycleStreamEvent(
    sessionId, runId, projectId, type.name, runStatus?.name, details.toString(), occurredAt.toString()
)
private val AGENT_SSE_JSON = Json { encodeDefaults = true }
