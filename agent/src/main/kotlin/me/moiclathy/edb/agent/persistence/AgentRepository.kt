package me.moiclathy.edb.agent.persistence

import me.moiclathy.edb.agent.model.AgentRun
import me.moiclathy.edb.agent.model.AgentProposalDecision
import me.moiclathy.edb.agent.model.AgentSession
import me.moiclathy.edb.agent.model.AgentWorkflowProposal

interface AgentRepository {
    fun saveSession(session: AgentSession): AgentSession
    fun updateSession(session: AgentSession): AgentSession
    fun findSession(id: String): AgentSession?

    fun saveRun(run: AgentRun): AgentRun
    fun updateRun(run: AgentRun): AgentRun
    fun findRun(id: String): AgentRun?
    fun findRunsBySession(sessionId: String): List<AgentRun>

    fun appendMessage(message: AgentMessageRecord): AgentMessageRecord
    fun findMessagesByRun(runId: String): List<AgentMessageRecord>
    fun findMemoryMessagesBySession(sessionId: String, limit: Int): List<AgentMessageRecord>

    fun saveToolCall(toolCall: AgentToolCallRecord): AgentToolCallRecord
    fun updateToolCall(toolCall: AgentToolCallRecord): AgentToolCallRecord
    fun findToolCallsByRun(runId: String): List<AgentToolCallRecord>

    fun saveProposal(proposal: AgentWorkflowProposal): AgentWorkflowProposal
    fun findProposal(id: String): AgentWorkflowProposal?
    fun findProposalsByRun(runId: String): List<AgentWorkflowProposal>
    fun saveProposalDecision(decision: AgentProposalDecision): AgentProposalDecision
    fun findProposalDecision(proposalId: String): AgentProposalDecision?

    fun appendAudit(event: AgentAuditEvent): AgentAuditEvent
    fun findAuditBySession(sessionId: String): List<AgentAuditEvent>
    fun findAuditByRun(runId: String): List<AgentAuditEvent>
}
