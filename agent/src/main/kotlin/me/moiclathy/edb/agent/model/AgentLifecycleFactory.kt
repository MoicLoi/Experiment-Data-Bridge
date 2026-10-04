package me.moiclathy.edb.agent.model

import java.time.Clock
import java.time.Instant
import java.util.UUID

class AgentLifecycleFactory(
    private val clock: Clock = Clock.systemUTC(),
    private val identifierSupplier: () -> String = { UUID.randomUUID().toString() }
) {
    fun createSession(definition: AgentDefinition, userId: Int, projectId: Int): AgentSession {
        val now = Instant.now(clock)
        return AgentSession(
            id = nextId("session"),
            userId = userId,
            projectId = projectId,
            agentId = definition.id,
            agentVersion = definition.version,
            status = AgentSessionStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
    }

    fun createRun(session: AgentSession, modelProfileId: String, correlationId: String): AgentRun {
        require(session.status == AgentSessionStatus.ACTIVE) { "Cannot create an Agent run for a closed session." }
        val now = Instant.now(clock)
        return AgentRun(
            id = nextId("run"),
            sessionId = session.id,
            correlationId = correlationId,
            status = AgentRunStatus.RECEIVED,
            modelProfileId = modelProfileId,
            startedAt = now
        )
    }

    private fun nextId(label: String): String = identifierSupplier().also {
        require(it.isNotBlank() && it == it.trim()) { "Generated Agent $label id must not be blank or padded." }
    }
}
