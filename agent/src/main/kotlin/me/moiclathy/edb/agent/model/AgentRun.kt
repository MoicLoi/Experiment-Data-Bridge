package me.moiclathy.edb.agent.model

import java.time.Instant

data class AgentRun(
    val id: String,
    val sessionId: String,
    val correlationId: String,
    val status: AgentRunStatus,
    val modelProfileId: String,
    val startedAt: Instant,
    val updatedAt: Instant = startedAt,
    val completedAt: Instant? = null,
    val tokenUsage: AgentTokenUsage? = null,
    val failure: AgentRunFailure? = null
) {
    init {
        requireIdentifier(id, "Agent run id")
        requireIdentifier(sessionId, "Agent session id")
        requireIdentifier(correlationId, "Agent correlation id")
        requireIdentifier(modelProfileId, "Model profile id")
        require(!updatedAt.isBefore(startedAt)) { "Agent run update time must not precede start time." }
        require(completedAt == null || !completedAt.isBefore(updatedAt)) {
            "Agent run completion time must not precede its last update."
        }
        require((status in TERMINAL_STATUSES) == (completedAt != null)) {
            "Only terminal Agent runs must have a completion time."
        }
        require((status == AgentRunStatus.FAILED) == (failure != null)) {
            "Only failed Agent runs must contain failure details."
        }
    }

    fun transitionTo(next: AgentRunStatus, at: Instant, failure: AgentRunFailure? = null): AgentRun {
        require(next in ALLOWED_TRANSITIONS.getValue(status)) { "Invalid Agent run transition: $status -> $next" }
        require(!at.isBefore(updatedAt)) { "Agent run transition time must not precede its last update." }
        require((next == AgentRunStatus.FAILED) == (failure != null)) {
            "A failed Agent run transition requires failure details, and other transitions forbid them."
        }
        return copy(
            status = next,
            updatedAt = at,
            completedAt = at.takeIf { next in TERMINAL_STATUSES },
            failure = failure
        )
    }

    fun withTokenUsage(usage: AgentTokenUsage, at: Instant): AgentRun {
        require(status !in TERMINAL_STATUSES) { "Token usage cannot change after an Agent run is terminal." }
        require(!at.isBefore(updatedAt)) { "Agent run update time must not precede its last update." }
        return copy(tokenUsage = usage, updatedAt = at)
    }

    companion object {
        private val TERMINAL_STATUSES = setOf(AgentRunStatus.COMPLETED, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED)
        private val ALLOWED_TRANSITIONS = mapOf(
            AgentRunStatus.RECEIVED to setOf(AgentRunStatus.PLANNING, AgentRunStatus.CANCELLED, AgentRunStatus.FAILED),
            AgentRunStatus.PLANNING to setOf(AgentRunStatus.VALIDATING, AgentRunStatus.CANCELLED, AgentRunStatus.FAILED),
            AgentRunStatus.VALIDATING to setOf(AgentRunStatus.PLANNING, AgentRunStatus.AWAITING_APPROVAL, AgentRunStatus.COMPLETED, AgentRunStatus.CANCELLED, AgentRunStatus.FAILED),
            AgentRunStatus.AWAITING_APPROVAL to setOf(AgentRunStatus.EXECUTING, AgentRunStatus.CANCELLED, AgentRunStatus.FAILED),
            AgentRunStatus.EXECUTING to setOf(AgentRunStatus.WAITING_TASK, AgentRunStatus.COMPLETED, AgentRunStatus.CANCELLED, AgentRunStatus.FAILED),
            AgentRunStatus.WAITING_TASK to setOf(AgentRunStatus.COMPLETED, AgentRunStatus.CANCELLED, AgentRunStatus.FAILED),
            AgentRunStatus.COMPLETED to emptySet(),
            AgentRunStatus.FAILED to emptySet(),
            AgentRunStatus.CANCELLED to emptySet()
        )
    }
}

enum class AgentRunStatus {
    RECEIVED,
    PLANNING,
    VALIDATING,
    AWAITING_APPROVAL,
    EXECUTING,
    WAITING_TASK,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AgentTokenUsage(
    val inputTokens: Long,
    val outputTokens: Long
) {
    init {
        require(inputTokens >= 0) { "Input token usage must not be negative." }
        require(outputTokens >= 0) { "Output token usage must not be negative." }
    }

    val totalTokens: Long get() = Math.addExact(inputTokens, outputTokens)
}

data class AgentRunFailure(val code: String, val message: String, val retryable: Boolean = false) {
    init {
        requireIdentifier(code, "Agent run failure code")
        require(message.isNotBlank()) { "Agent run failure message must not be blank." }
    }
}
