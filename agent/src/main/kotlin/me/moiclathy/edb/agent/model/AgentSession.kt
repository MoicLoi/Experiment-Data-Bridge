package me.moiclathy.edb.agent.model

import java.time.Instant

data class AgentSession(
    val id: String,
    val userId: Int,
    val projectId: Int,
    val agentId: String,
    val agentVersion: String,
    val status: AgentSessionStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        requireIdentifier(id, "Agent session id")
        require(userId > 0) { "Agent session user id must be greater than zero." }
        require(projectId > 0) { "Agent session project id must be greater than zero." }
        requireIdentifier(agentId, "Agent id")
        requireIdentifier(agentVersion, "Agent version")
        require(!updatedAt.isBefore(createdAt)) { "Agent session update time must not precede creation time." }
    }

    fun close(at: Instant): AgentSession {
        require(status == AgentSessionStatus.ACTIVE) { "Only an active Agent session can be closed." }
        require(!at.isBefore(updatedAt)) { "Agent session close time must not precede its last update." }
        return copy(status = AgentSessionStatus.CLOSED, updatedAt = at)
    }
}

enum class AgentSessionStatus {
    ACTIVE,
    CLOSED
}
