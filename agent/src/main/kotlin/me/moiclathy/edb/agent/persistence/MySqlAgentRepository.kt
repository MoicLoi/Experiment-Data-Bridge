package me.moiclathy.edb.agent.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.model.AgentProposalValidation
import me.moiclathy.edb.agent.model.AgentProposalDecision
import me.moiclathy.edb.agent.model.AgentProposalValidationError
import me.moiclathy.edb.agent.model.AgentRun
import me.moiclathy.edb.agent.model.AgentRunFailure
import me.moiclathy.edb.agent.model.AgentSession
import me.moiclathy.edb.agent.model.AgentTokenUsage
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class MySqlAgentRepository(
    private val json: Json = Json
) : AgentRepository {
    override fun saveSession(session: AgentSession): AgentSession = transaction {
        AgentSessionsTable.insert { row ->
            row[id] = session.id; row[userId] = session.userId; row[projectId] = session.projectId
            row[agentId] = session.agentId; row[agentVersion] = session.agentVersion; row[status] = session.status
            row[createdAt] = session.createdAt; row[updatedAt] = session.updatedAt
        }
        session
    }

    override fun updateSession(session: AgentSession): AgentSession = transaction {
        check(AgentSessionsTable.update({ AgentSessionsTable.id eq session.id }) { row ->
            row[status] = session.status; row[updatedAt] = session.updatedAt
        } == 1) { "Agent session was not found: ${session.id}" }
        session
    }

    override fun findSession(id: String): AgentSession? = transaction {
        AgentSessionsTable.selectAll().where { AgentSessionsTable.id eq id }.limit(1).singleOrNull()?.toSession()
    }

    override fun saveRun(run: AgentRun): AgentRun = transaction {
        AgentRunsTable.insert { row -> row.writeRun(run, includeImmutable = true) }
        run
    }

    override fun updateRun(run: AgentRun): AgentRun = transaction {
        check(AgentRunsTable.update({ AgentRunsTable.id eq run.id }) { row -> row.writeRun(run, includeImmutable = false) } == 1) {
            "Agent run was not found: ${run.id}"
        }
        run
    }

    override fun findRun(id: String): AgentRun? = transaction {
        AgentRunsTable.selectAll().where { AgentRunsTable.id eq id }.limit(1).singleOrNull()?.toRun()
    }

    override fun findRunsBySession(sessionId: String): List<AgentRun> = transaction {
        AgentRunsTable.selectAll().where { AgentRunsTable.sessionId eq sessionId }
            .orderBy(AgentRunsTable.startedAt to SortOrder.ASC).map(ResultRow::toRun)
    }

    override fun appendMessage(message: AgentMessageRecord): AgentMessageRecord = transaction {
        require(message.id == null) { "A new Agent message must not have an id." }
        val id = AgentMessagesTable.insert { row ->
            row[sessionId] = message.sessionId; row[runId] = message.runId; row[role] = message.role
            row[content] = message.content; row[toolCallId] = message.toolCallId
            row[memoryEligible] = message.memoryEligible; row[createdAt] = message.createdAt
        }.resultedValues?.single()?.get(AgentMessagesTable.id)?.value ?: error("Failed to append Agent message")
        message.copy(id = id)
    }

    override fun findMessagesByRun(runId: String): List<AgentMessageRecord> = transaction {
        AgentMessagesTable.selectAll().where { AgentMessagesTable.runId eq runId }
            .orderBy(AgentMessagesTable.createdAt to SortOrder.ASC, AgentMessagesTable.id to SortOrder.ASC)
            .map(ResultRow::toMessage)
    }

    override fun findMemoryMessagesBySession(sessionId: String, limit: Int): List<AgentMessageRecord> = transaction {
        require(limit > 0) { "Agent memory message limit must be greater than zero." }
        AgentMessagesTable.selectAll().where {
            (AgentMessagesTable.sessionId eq sessionId) and (AgentMessagesTable.memoryEligible eq true)
        }.orderBy(AgentMessagesTable.createdAt to SortOrder.DESC, AgentMessagesTable.id to SortOrder.DESC)
            .limit(limit).map(ResultRow::toMessage).reversed()
    }

    override fun saveToolCall(toolCall: AgentToolCallRecord): AgentToolCallRecord = transaction {
        require(toolCall.id == null) { "A new Agent tool call must not have an id." }
        val id = AgentToolCallsTable.insert { row -> row.writeToolCall(toolCall, includeImmutable = true) }
            .resultedValues?.single()?.get(AgentToolCallsTable.id)?.value ?: error("Failed to save Agent tool call")
        toolCall.copy(id = id)
    }

    override fun updateToolCall(toolCall: AgentToolCallRecord): AgentToolCallRecord = transaction {
        val id = requireNotNull(toolCall.id) { "A persisted Agent tool call must have an id." }
        check(AgentToolCallsTable.update({ AgentToolCallsTable.id eq id }) { row ->
            row.writeToolCall(toolCall, includeImmutable = false)
        } == 1) { "Agent tool call was not found: $id" }
        toolCall
    }

    override fun findToolCallsByRun(runId: String): List<AgentToolCallRecord> = transaction {
        AgentToolCallsTable.selectAll().where { AgentToolCallsTable.runId eq runId }
            .orderBy(AgentToolCallsTable.startedAt to SortOrder.ASC, AgentToolCallsTable.id to SortOrder.ASC)
            .map { it.toToolCall(json) }
    }

    override fun saveProposal(proposal: AgentWorkflowProposal): AgentWorkflowProposal = transaction {
        AgentWorkflowProposalsTable.insert { row ->
            row[id] = proposal.id; row[runId] = proposal.runId; row[workflowIrJson] = proposal.workflowIrJson
            row[explanation] = proposal.explanation; row[assumptionsJson] = proposal.assumptions.toStringListJson()
            row[warningsJson] = proposal.warnings.toStringListJson(); row[validationValid] = proposal.validation.valid
            row[executionOrderJson] = proposal.validation.executionOrder.toStringListJson()
            row[validationErrorsJson] = proposal.validation.errors.toValidationErrorsJson()
            row[approvalRequired] = proposal.approvalRequired; row[createdAt] = proposal.createdAt
        }
        proposal
    }

    override fun findProposal(id: String): AgentWorkflowProposal? = transaction {
        AgentWorkflowProposalsTable.selectAll().where { AgentWorkflowProposalsTable.id eq id }
            .limit(1).singleOrNull()?.toProposal(json)
    }

    override fun findProposalsByRun(runId: String): List<AgentWorkflowProposal> = transaction {
        AgentWorkflowProposalsTable.selectAll().where { AgentWorkflowProposalsTable.runId eq runId }
            .orderBy(AgentWorkflowProposalsTable.createdAt to SortOrder.ASC).map { it.toProposal(json) }
    }

    override fun saveProposalDecision(decision: AgentProposalDecision): AgentProposalDecision = transaction {
        AgentProposalDecisionsTable.insert { row ->
            row[proposalId] = decision.proposalId
            row[status] = decision.status
            row[decidedBy] = decision.decidedBy
            row[workflowSha256] = decision.workflowSha256
            row[reason] = decision.reason
            row[decidedAt] = decision.decidedAt
        }
        decision
    }

    override fun findProposalDecision(proposalId: String): AgentProposalDecision? = transaction {
        AgentProposalDecisionsTable.selectAll()
            .where { AgentProposalDecisionsTable.proposalId eq proposalId }
            .limit(1)
            .singleOrNull()
            ?.toProposalDecision()
    }

    override fun appendAudit(event: AgentAuditEvent): AgentAuditEvent = transaction {
        require(event.id == null) { "A new Agent audit event must not have an id." }
        val id = AgentAuditEventsTable.insert { row ->
            row[sessionId] = event.sessionId; row[runId] = event.runId; row[actorUserId] = event.actorUserId
            row[projectId] = event.projectId; row[eventType] = event.type; row[runStatus] = event.runStatus
            row[detailsJson] = event.details.takeIf { it.isNotEmpty() }?.toString(); row[createdAt] = event.createdAt
        }.resultedValues?.single()?.get(AgentAuditEventsTable.id)?.value ?: error("Failed to append Agent audit event")
        event.copy(id = id)
    }

    override fun findAuditByRun(runId: String): List<AgentAuditEvent> = transaction {
        AgentAuditEventsTable.selectAll().where { AgentAuditEventsTable.runId eq runId }
            .orderBy(AgentAuditEventsTable.createdAt to SortOrder.ASC, AgentAuditEventsTable.id to SortOrder.ASC)
            .map { it.toAudit(json) }
    }

    override fun findAuditBySession(sessionId: String): List<AgentAuditEvent> = transaction {
        AgentAuditEventsTable.selectAll().where { AgentAuditEventsTable.sessionId eq sessionId }
            .orderBy(AgentAuditEventsTable.createdAt to SortOrder.ASC, AgentAuditEventsTable.id to SortOrder.ASC)
            .map { it.toAudit(json) }
    }
}

private fun ResultRow.toSession() = AgentSession(
    id = this[AgentSessionsTable.id], userId = this[AgentSessionsTable.userId], projectId = this[AgentSessionsTable.projectId],
    agentId = this[AgentSessionsTable.agentId], agentVersion = this[AgentSessionsTable.agentVersion],
    status = this[AgentSessionsTable.status], createdAt = this[AgentSessionsTable.createdAt], updatedAt = this[AgentSessionsTable.updatedAt]
)

private fun org.jetbrains.exposed.sql.statements.UpdateBuilder<*>.writeRun(run: AgentRun, includeImmutable: Boolean) {
    if (includeImmutable) {
        this[AgentRunsTable.id] = run.id; this[AgentRunsTable.sessionId] = run.sessionId
        this[AgentRunsTable.correlationId] = run.correlationId; this[AgentRunsTable.modelProfileId] = run.modelProfileId
        this[AgentRunsTable.startedAt] = run.startedAt
    }
    this[AgentRunsTable.status] = run.status; this[AgentRunsTable.updatedAt] = run.updatedAt
    this[AgentRunsTable.completedAt] = run.completedAt; this[AgentRunsTable.inputTokens] = run.tokenUsage?.inputTokens
    this[AgentRunsTable.outputTokens] = run.tokenUsage?.outputTokens; this[AgentRunsTable.failureCode] = run.failure?.code
    this[AgentRunsTable.failureMessage] = run.failure?.message; this[AgentRunsTable.failureRetryable] = run.failure?.retryable
}

private fun ResultRow.toRun() = AgentRun(
    id = this[AgentRunsTable.id], sessionId = this[AgentRunsTable.sessionId], correlationId = this[AgentRunsTable.correlationId],
    status = this[AgentRunsTable.status], modelProfileId = this[AgentRunsTable.modelProfileId], startedAt = this[AgentRunsTable.startedAt],
    updatedAt = this[AgentRunsTable.updatedAt], completedAt = this[AgentRunsTable.completedAt],
    tokenUsage = this[AgentRunsTable.inputTokens]?.let { AgentTokenUsage(it, this[AgentRunsTable.outputTokens] ?: 0) },
    failure = this[AgentRunsTable.failureCode]?.let { AgentRunFailure(it, requireNotNull(this[AgentRunsTable.failureMessage]), this[AgentRunsTable.failureRetryable] ?: false) }
)

private fun ResultRow.toMessage() = AgentMessageRecord(
    id = this[AgentMessagesTable.id].value, sessionId = this[AgentMessagesTable.sessionId], runId = this[AgentMessagesTable.runId],
    role = this[AgentMessagesTable.role], content = this[AgentMessagesTable.content], toolCallId = this[AgentMessagesTable.toolCallId],
    memoryEligible = this[AgentMessagesTable.memoryEligible],
    createdAt = this[AgentMessagesTable.createdAt]
)

private fun org.jetbrains.exposed.sql.statements.UpdateBuilder<*>.writeToolCall(call: AgentToolCallRecord, includeImmutable: Boolean) {
    if (includeImmutable) {
        this[AgentToolCallsTable.runId] = call.runId; this[AgentToolCallsTable.toolCallId] = call.toolCallId
        this[AgentToolCallsTable.toolName] = call.toolName; this[AgentToolCallsTable.capability] = call.capability
        this[AgentToolCallsTable.argumentsJson] = call.arguments.toString(); this[AgentToolCallsTable.startedAt] = call.startedAt
    }
    this[AgentToolCallsTable.resultJson] = call.result?.toString(); this[AgentToolCallsTable.status] = call.status
    this[AgentToolCallsTable.errorCode] = call.errorCode; this[AgentToolCallsTable.errorMessage] = call.errorMessage
    this[AgentToolCallsTable.completedAt] = call.completedAt
}

private fun ResultRow.toToolCall(json: Json) = AgentToolCallRecord(
    id = this[AgentToolCallsTable.id].value, runId = this[AgentToolCallsTable.runId], toolCallId = this[AgentToolCallsTable.toolCallId],
    toolName = this[AgentToolCallsTable.toolName], capability = this[AgentToolCallsTable.capability],
    arguments = json.parseToJsonElement(this[AgentToolCallsTable.argumentsJson]).jsonObject,
    result = this[AgentToolCallsTable.resultJson]?.let { json.parseToJsonElement(it).jsonObject },
    status = this[AgentToolCallsTable.status], errorCode = this[AgentToolCallsTable.errorCode],
    errorMessage = this[AgentToolCallsTable.errorMessage], startedAt = this[AgentToolCallsTable.startedAt],
    completedAt = this[AgentToolCallsTable.completedAt]
)

private fun ResultRow.toProposal(json: Json): AgentWorkflowProposal {
    val errors = json.parseToJsonElement(this[AgentWorkflowProposalsTable.validationErrorsJson]) as JsonArray
    return AgentWorkflowProposal(
        id = this[AgentWorkflowProposalsTable.id], runId = this[AgentWorkflowProposalsTable.runId],
        workflowIrJson = this[AgentWorkflowProposalsTable.workflowIrJson], explanation = this[AgentWorkflowProposalsTable.explanation],
        assumptions = json.stringList(this[AgentWorkflowProposalsTable.assumptionsJson]),
        warnings = json.stringList(this[AgentWorkflowProposalsTable.warningsJson]),
        validation = AgentProposalValidation(
            valid = this[AgentWorkflowProposalsTable.validationValid],
            executionOrder = json.stringList(this[AgentWorkflowProposalsTable.executionOrderJson]),
            errors = errors.map { item -> AgentProposalValidationError(item.jsonObject.getValue("path").primitiveContent(), item.jsonObject.getValue("message").primitiveContent()) }
        ),
        approvalRequired = this[AgentWorkflowProposalsTable.approvalRequired], createdAt = this[AgentWorkflowProposalsTable.createdAt]
    )
}

private fun ResultRow.toAudit(json: Json) = AgentAuditEvent(
    id = this[AgentAuditEventsTable.id].value, sessionId = this[AgentAuditEventsTable.sessionId], runId = this[AgentAuditEventsTable.runId],
    actorUserId = this[AgentAuditEventsTable.actorUserId], projectId = this[AgentAuditEventsTable.projectId],
    type = this[AgentAuditEventsTable.eventType], runStatus = this[AgentAuditEventsTable.runStatus],
    details = this[AgentAuditEventsTable.detailsJson]?.let { json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap()),
    createdAt = this[AgentAuditEventsTable.createdAt]
)

private fun ResultRow.toProposalDecision() = AgentProposalDecision(
    proposalId = this[AgentProposalDecisionsTable.proposalId],
    status = this[AgentProposalDecisionsTable.status],
    decidedBy = this[AgentProposalDecisionsTable.decidedBy],
    workflowSha256 = this[AgentProposalDecisionsTable.workflowSha256],
    reason = this[AgentProposalDecisionsTable.reason],
    decidedAt = this[AgentProposalDecisionsTable.decidedAt]
)

private fun List<String>.toStringListJson() = buildJsonArray { forEach { add(JsonPrimitive(it)) } }.toString()
private fun List<AgentProposalValidationError>.toValidationErrorsJson() = buildJsonArray { forEach { error -> add(buildJsonObject {
    put("path", error.path); put("message", error.message)
}) } }.toString()
private fun Json.stringList(document: String) = (parseToJsonElement(document) as JsonArray).map { it.primitiveContent() }
private fun kotlinx.serialization.json.JsonElement.primitiveContent() = (this as JsonPrimitive).content
