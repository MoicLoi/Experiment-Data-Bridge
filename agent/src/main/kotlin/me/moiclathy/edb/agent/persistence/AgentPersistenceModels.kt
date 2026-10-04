package me.moiclathy.edb.agent.persistence

import kotlinx.serialization.json.JsonObject
import me.moiclathy.edb.agent.model.AgentRunStatus
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.provider.AgentChatRole
import java.time.Instant

data class AgentMessageRecord(
    val id: Long? = null,
    val sessionId: String,
    val runId: String,
    val role: AgentChatRole,
    val content: String,
    val toolCallId: String? = null,
    val memoryEligible: Boolean = false,
    val createdAt: Instant
) {
    init {
        require(sessionId.isNotBlank()) { "Agent message session id must not be blank." }
        require(runId.isNotBlank()) { "Agent message run id must not be blank." }
        require(content.isNotBlank()) { "Agent message content must not be blank." }
        require((role == AgentChatRole.TOOL) == (toolCallId != null)) {
            "Only Agent tool-result messages require a tool call id."
        }
    }
}

data class AgentToolCallRecord(
    val id: Long? = null,
    val runId: String,
    val toolCallId: String,
    val toolName: String,
    val capability: AgentCapability,
    val arguments: JsonObject,
    val result: JsonObject? = null,
    val status: AgentToolCallStatus = AgentToolCallStatus.STARTED,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val startedAt: Instant,
    val completedAt: Instant? = null
) {
    init {
        require(runId.isNotBlank()) { "Agent tool-call run id must not be blank." }
        require(toolCallId.isNotBlank()) { "Agent tool call id must not be blank." }
        require(toolName.isNotBlank()) { "Agent tool name must not be blank." }
        require((status == AgentToolCallStatus.STARTED) == (completedAt == null)) {
            "Only a started Agent tool call has no completion time."
        }
        require((status == AgentToolCallStatus.FAILED) == (errorMessage != null)) {
            "Only a failed Agent tool call must contain an error message."
        }
    }
}

enum class AgentToolCallStatus {
    STARTED,
    COMPLETED,
    FAILED
}

data class AgentAuditEvent(
    val id: Long? = null,
    val sessionId: String,
    val runId: String?,
    val actorUserId: Int,
    val projectId: Int,
    val type: AgentAuditEventType,
    val runStatus: AgentRunStatus? = null,
    val details: JsonObject = JsonObject(emptyMap()),
    val createdAt: Instant
) {
    init {
        require(sessionId.isNotBlank()) { "Agent audit session id must not be blank." }
        require(runId == null || runId.isNotBlank()) { "Agent audit run id must not be blank." }
        require(actorUserId > 0) { "Agent audit actor user id must be greater than zero." }
        require(projectId > 0) { "Agent audit project id must be greater than zero." }
    }
}

enum class AgentAuditEventType {
    SESSION_CREATED,
    SESSION_CLOSED,
    RUN_CREATED,
    RUN_STARTED,
    RUN_SNAPSHOT,
    MESSAGE_DELTA,
    MODEL_REQUESTED,
    MODEL_RESPONDED,
    MODEL_FAILED,
    TOOL_STARTED,
    TOOL_COMPLETED,
    TOOL_FAILED,
    VALIDATION_FAILED,
    REPAIR_REQUESTED,
    PROPOSAL_CREATED,
    PROPOSAL_APPROVED,
    PROPOSAL_REJECTED,
    WORKFLOW_DEFINITION_CREATED,
    TASK_SUBMITTED,
    RUN_COMPLETED,
    RUN_FAILED,
    RUN_CANCELLED,
    PROMPT_REJECTED
}
