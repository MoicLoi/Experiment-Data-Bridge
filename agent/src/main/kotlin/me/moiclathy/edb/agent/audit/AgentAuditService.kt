package me.moiclathy.edb.agent.audit

import kotlinx.serialization.json.JsonObject
import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentRunStatus
import me.moiclathy.edb.agent.model.AgentSession
import me.moiclathy.edb.agent.persistence.AgentAuditEvent
import me.moiclathy.edb.agent.persistence.AgentAuditEventType
import me.moiclathy.edb.agent.persistence.AgentRepository
import java.time.Clock
import java.time.Instant
import me.moiclathy.edb.agent.policy.AgentSensitiveDataRedactor

class AgentAuditService(
    private val repository: AgentRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val redactor: AgentSensitiveDataRedactor = AgentSensitiveDataRedactor()
) {
    fun recordSession(session: AgentSession, type: AgentAuditEventType, details: JsonObject = JsonObject(emptyMap())) =
        repository.appendAudit(
            AgentAuditEvent(
                sessionId = session.id,
                runId = null,
                actorUserId = session.userId,
                projectId = session.projectId,
                type = type,
                details = redactor.redact(details),
                createdAt = Instant.now(clock)
            )
        )

    fun record(
        context: AgentExecutionContext,
        type: AgentAuditEventType,
        runStatus: AgentRunStatus? = null,
        details: JsonObject = JsonObject(emptyMap()),
        at: Instant = Instant.now(clock)
    ) = repository.appendAudit(
        AgentAuditEvent(
            sessionId = context.sessionId,
            runId = context.runId,
            actorUserId = context.userId,
            projectId = context.projectId,
            type = type,
            runStatus = runStatus,
            details = redactor.redact(details),
            createdAt = at
        )
    )
}
