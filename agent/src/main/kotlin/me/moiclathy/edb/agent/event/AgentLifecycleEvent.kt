package me.moiclathy.edb.agent.event

import kotlinx.serialization.json.JsonObject
import me.moiclathy.edb.agent.model.AgentRunStatus
import me.moiclathy.edb.agent.persistence.AgentAuditEventType
import me.moiclathy.edb.core.event.Event
import java.time.Instant

data class AgentLifecycleEvent(
    val sessionId: String,
    val runId: String?,
    val actorUserId: Int,
    val projectId: Int,
    val type: AgentAuditEventType,
    val runStatus: AgentRunStatus? = null,
    val details: JsonObject = JsonObject(emptyMap()),
    override val occurredAt: Instant
) : Event
