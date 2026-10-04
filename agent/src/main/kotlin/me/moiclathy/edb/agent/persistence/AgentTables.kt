package me.moiclathy.edb.agent.persistence

import me.moiclathy.edb.agent.model.AgentRunStatus
import me.moiclathy.edb.agent.model.AgentProposalDecisionStatus
import me.moiclathy.edb.agent.model.AgentSessionStatus
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.provider.AgentChatRole
import me.moiclathy.edb.core.database.table.ProjectsTable
import me.moiclathy.edb.core.database.table.UsersTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.date

object AgentSessionsTable : Table("agent_sessions") {
    val id = varchar("id", 64)
    val userId = reference("user_id", UsersTable.id)
    val projectId = reference("project_id", ProjectsTable.id)
    val agentId = varchar("agent_id", 100)
    val agentVersion = varchar("agent_version", 50)
    val status = enumerationByName("status", 20, AgentSessionStatus::class)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AgentRunsTable : Table("agent_runs") {
    val id = varchar("id", 64)
    val sessionId = reference("session_id", AgentSessionsTable.id)
    val correlationId = varchar("correlation_id", 100).uniqueIndex()
    val status = enumerationByName("status", 30, AgentRunStatus::class)
    val modelProfileId = varchar("model_profile_id", 100)
    val inputTokens = long("input_tokens").nullable()
    val outputTokens = long("output_tokens").nullable()
    val failureCode = varchar("failure_code", 100).nullable()
    val failureMessage = text("failure_message").nullable()
    val failureRetryable = bool("failure_retryable").nullable()
    val startedAt = timestamp("started_at")
    val updatedAt = timestamp("updated_at")
    val completedAt = timestamp("completed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object AgentMessagesTable : LongIdTable("agent_messages") {
    val sessionId = reference("session_id", AgentSessionsTable.id)
    val runId = reference("run_id", AgentRunsTable.id)
    val role = enumerationByName("role", 20, AgentChatRole::class)
    val content = text("content")
    val toolCallId = varchar("tool_call_id", 100).nullable()
    val memoryEligible = bool("memory_eligible").default(false)
    val createdAt = timestamp("created_at")
}

object AgentToolCallsTable : LongIdTable("agent_tool_calls") {
    val runId = reference("run_id", AgentRunsTable.id)
    val toolCallId = varchar("tool_call_id", 100)
    val toolName = varchar("tool_name", 100)
    val capability = enumerationByName("capability", 40, AgentCapability::class)
    val argumentsJson = text("arguments_json")
    val resultJson = text("result_json").nullable()
    val status = enumerationByName("status", 20, AgentToolCallStatus::class)
    val errorCode = varchar("error_code", 100).nullable()
    val errorMessage = text("error_message").nullable()
    val startedAt = timestamp("started_at")
    val completedAt = timestamp("completed_at").nullable()

    init {
        uniqueIndex(runId, toolCallId)
    }
}

object AgentWorkflowProposalsTable : Table("agent_workflow_proposals") {
    val id = varchar("id", 64)
    val runId = reference("run_id", AgentRunsTable.id)
    val workflowIrJson = text("workflow_ir_json")
    val explanation = text("explanation")
    val assumptionsJson = text("assumptions_json")
    val warningsJson = text("warnings_json")
    val validationValid = bool("validation_valid")
    val executionOrderJson = text("execution_order_json")
    val validationErrorsJson = text("validation_errors_json")
    val approvalRequired = bool("approval_required")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AgentProposalDecisionsTable : Table("agent_proposal_decisions") {
    val proposalId = reference("proposal_id", AgentWorkflowProposalsTable.id)
    val status = enumerationByName("status", 20, AgentProposalDecisionStatus::class)
    val decidedBy = reference("decided_by", UsersTable.id)
    val workflowSha256 = char("workflow_sha256", 64)
    val reason = text("reason").nullable()
    val decidedAt = timestamp("decided_at")
    override val primaryKey = PrimaryKey(proposalId)
}

object AgentAuditEventsTable : LongIdTable("agent_audit_events") {
    val sessionId = reference("session_id", AgentSessionsTable.id)
    val runId = reference("run_id", AgentRunsTable.id).nullable()
    val actorUserId = reference("actor_user_id", UsersTable.id)
    val projectId = reference("project_id", ProjectsTable.id)
    val eventType = enumerationByName("event_type", 40, AgentAuditEventType::class)
    val runStatus = enumerationByName("run_status", 30, AgentRunStatus::class).nullable()
    val detailsJson = text("details_json").nullable()
    val createdAt = timestamp("created_at")
}

object AgentTokenQuotaUsageTable : LongIdTable("agent_token_quota_usage") {
    val scopeType = varchar("scope_type", 20)
    val scopeId = integer("scope_id")
    val usageDate = date("usage_date")
    val usedTokens = long("used_tokens")
    val updatedAt = timestamp("updated_at")

    init { uniqueIndex(scopeType, scopeId, usageDate) }
}
