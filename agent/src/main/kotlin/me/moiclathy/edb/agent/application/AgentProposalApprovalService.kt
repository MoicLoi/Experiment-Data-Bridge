package me.moiclathy.edb.agent.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.audit.AgentAuditService
import me.moiclathy.edb.agent.event.AgentEventPublisher
import me.moiclathy.edb.agent.event.AgentLifecycleEvent
import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentProposalDecision
import me.moiclathy.edb.agent.model.AgentProposalDecisionStatus
import me.moiclathy.edb.agent.persistence.AgentAuditEventType
import me.moiclathy.edb.agent.persistence.AgentRepository
import me.moiclathy.edb.agent.tools.WorkflowPlanningTools
import me.moiclathy.edb.core.exception.EDBException
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

/** Human decision boundary. Approval validates the immutable proposal again but never executes it. */
class AgentProposalApprovalService(
    private val repository: AgentRepository,
    private val authorization: AgentAuthorization,
    private val tools: WorkflowPlanningTools,
    private val audit: AgentAuditService,
    private val eventPublisher: AgentEventPublisher = AgentEventPublisher.None,
    private val clock: Clock = Clock.systemUTC()
) {
    fun getDecision(proposalId: String, requestedBy: Int): AgentProposalDecision? {
        requireOwnedProposal(proposalId, requestedBy)
        return repository.findProposalDecision(proposalId)
    }

    suspend fun approve(proposalId: String, requestedBy: Int): AgentProposalDecision {
        val aggregate = requireOwnedProposal(proposalId, requestedBy)
        authorization.requireWritable(requestedBy, aggregate.session.projectId)
        repository.findProposalDecision(proposalId)?.let { existing ->
            if (existing.status == AgentProposalDecisionStatus.APPROVED) return existing
            throw AgentProposalDecisionConflictException(proposalId, existing.status)
        }
        if (!aggregate.proposal.validation.valid) throw AgentProposalInvalidException(proposalId)
        val validation = tools.validate(Json.parseToJsonElement(aggregate.proposal.workflowIrJson))
        val canonical = validation.canonicalWorkflowIrJson ?: throw AgentProposalInvalidException(proposalId)
        val decision = AgentProposalDecision(
            proposalId = proposalId,
            status = AgentProposalDecisionStatus.APPROVED,
            decidedBy = requestedBy,
            workflowSha256 = canonical.sha256(),
            decidedAt = Instant.now(clock)
        )
        return persistAndPublish(aggregate.context, decision, AgentAuditEventType.PROPOSAL_APPROVED)
    }

    suspend fun reject(proposalId: String, requestedBy: Int, reason: String): AgentProposalDecision {
        require(reason.isNotBlank()) { "Proposal rejection reason must not be blank." }
        val aggregate = requireOwnedProposal(proposalId, requestedBy)
        authorization.requireWritable(requestedBy, aggregate.session.projectId)
        repository.findProposalDecision(proposalId)?.let { existing ->
            if (existing.status == AgentProposalDecisionStatus.REJECTED && existing.reason == reason.trim()) return existing
            throw AgentProposalDecisionConflictException(proposalId, existing.status)
        }
        val decision = AgentProposalDecision(
            proposalId = proposalId,
            status = AgentProposalDecisionStatus.REJECTED,
            decidedBy = requestedBy,
            workflowSha256 = aggregate.proposal.workflowIrJson.sha256(),
            reason = reason.trim(),
            decidedAt = Instant.now(clock)
        )
        return persistAndPublish(aggregate.context, decision, AgentAuditEventType.PROPOSAL_REJECTED)
    }

    private suspend fun persistAndPublish(
        context: AgentExecutionContext,
        decision: AgentProposalDecision,
        type: AgentAuditEventType
    ): AgentProposalDecision {
        val saved = try {
            repository.saveProposalDecision(decision)
        } catch (failure: Exception) {
            val concurrent = repository.findProposalDecision(decision.proposalId) ?: throw failure
            if (concurrent.sameDecisionAs(decision)) return concurrent
            throw AgentProposalDecisionConflictException(decision.proposalId, concurrent.status)
        }
        val details = buildJsonObject {
            put("proposalId", decision.proposalId)
            put("workflowSha256", decision.workflowSha256)
            decision.reason?.let { put("reason", it) }
        }
        audit.record(context, type, details = details, at = decision.decidedAt)
        eventPublisher.publish(AgentLifecycleEvent(
            context.sessionId, context.runId, context.userId, context.projectId,
            type, details = details, occurredAt = decision.decidedAt
        ))
        return saved
    }

    private fun requireOwnedProposal(proposalId: String, requestedBy: Int): ProposalAggregate {
        val proposal = repository.findProposal(proposalId) ?: throw AgentProposalNotFoundException(proposalId)
        val run = repository.findRun(proposal.runId) ?: throw AgentRunNotFoundException(proposal.runId)
        val session = repository.findSession(run.sessionId) ?: throw AgentSessionNotFoundException(run.sessionId)
        if (session.userId != requestedBy) throw AgentSessionAccessDeniedException(session.id)
        authorization.requireReadable(requestedBy, session.projectId)
        return ProposalAggregate(proposal, session, AgentExecutionContext(
            session.id, run.id, requestedBy, session.projectId, run.correlationId
        ))
    }
}

private data class ProposalAggregate(
    val proposal: me.moiclathy.edb.agent.model.AgentWorkflowProposal,
    val session: me.moiclathy.edb.agent.model.AgentSession,
    val context: AgentExecutionContext
)

class AgentProposalInvalidException(id: String) :
    EDBException("AGENT_PROPOSAL_INVALID", "Agent proposal is no longer valid: $id")

class AgentProposalDecisionConflictException(id: String, status: AgentProposalDecisionStatus) :
    EDBException("AGENT_PROPOSAL_DECIDED", "Agent proposal $id was already ${status.name.lowercase()}.")

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private fun AgentProposalDecision.sameDecisionAs(other: AgentProposalDecision): Boolean =
    status == other.status && workflowSha256 == other.workflowSha256 && reason == other.reason
