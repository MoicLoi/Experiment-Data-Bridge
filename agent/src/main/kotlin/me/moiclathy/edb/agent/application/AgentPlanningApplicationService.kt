package me.moiclathy.edb.agent.application

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.audit.AgentAuditService
import me.moiclathy.edb.agent.event.AgentEventPublisher
import me.moiclathy.edb.agent.event.AgentLifecycleEvent
import me.moiclathy.edb.agent.model.AgentDefinition
import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentLifecycleFactory
import me.moiclathy.edb.agent.model.AgentRun
import me.moiclathy.edb.agent.model.AgentRunFailure
import me.moiclathy.edb.agent.model.AgentRunStatus
import me.moiclathy.edb.agent.model.AgentSession
import me.moiclathy.edb.agent.model.AgentSessionStatus
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import me.moiclathy.edb.agent.persistence.AgentAuditEventType
import me.moiclathy.edb.agent.persistence.AgentRepository
import me.moiclathy.edb.agent.persistence.AgentAuditEvent
import me.moiclathy.edb.agent.persistence.AgentMessageRecord
import me.moiclathy.edb.agent.planning.WorkflowPlanningAgent
import me.moiclathy.edb.agent.planning.WorkflowPlanningCommand
import me.moiclathy.edb.agent.planning.WorkflowPlanningException
import me.moiclathy.edb.agent.policy.AgentPolicy
import me.moiclathy.edb.agent.policy.AgentPromptSecurity
import me.moiclathy.edb.agent.policy.AgentPromptInjectionRejectedException
import me.moiclathy.edb.agent.policy.AgentSensitiveDataRedactor
import me.moiclathy.edb.core.exception.EDBException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException

data class AgentPlanningResult(
    val session: AgentSession,
    val run: AgentRun,
    val proposal: AgentWorkflowProposal
)

class AgentPlanningApplicationService(
    private val planner: WorkflowPlanningAgent,
    private val repository: AgentRepository,
    private val audit: AgentAuditService,
    private val authorization: AgentAuthorization,
    private val eventPublisher: AgentEventPublisher = AgentEventPublisher.None,
    private val chatMemory: AgentChatMemory? = null,
    private val tokenQuota: AgentTokenQuotaService? = null,
    private val promptSecurity: AgentPromptSecurity = AgentPromptSecurity(),
    private val redactor: AgentSensitiveDataRedactor = AgentSensitiveDataRedactor(),
    private val lifecycleFactory: AgentLifecycleFactory = AgentLifecycleFactory(),
    private val clock: Clock = Clock.systemUTC(),
    private val correlationIdSupplier: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun createSession(definition: AgentDefinition, userId: Int, projectId: Int): AgentSession {
        authorization.requireReadable(userId, projectId)
        val session = repository.saveSession(lifecycleFactory.createSession(definition, userId, projectId))
        audit.recordSession(session, AgentAuditEventType.SESSION_CREATED, buildJsonObject {
            put("agentId", definition.id); put("agentVersion", definition.version)
        })
        eventPublisher.publish(session.event(AgentAuditEventType.SESSION_CREATED))
        return session
    }

    suspend fun closeSession(sessionId: String, requestedBy: Int): AgentSession {
        val session = requireSession(sessionId)
        requireSameUser(session, requestedBy)
        authorization.requireReadable(requestedBy, session.projectId)
        val closed = repository.updateSession(session.close(Instant.now(clock)))
        audit.recordSession(closed, AgentAuditEventType.SESSION_CLOSED)
        eventPublisher.publish(closed.event(AgentAuditEventType.SESSION_CLOSED))
        return closed
    }

    fun getSession(sessionId: String, requestedBy: Int): AgentSession = requireSession(sessionId).also {
        requireSameUser(it, requestedBy)
        authorization.requireReadable(requestedBy, it.projectId)
    }

    fun getRun(runId: String, requestedBy: Int): AgentRun {
        val run = repository.findRun(runId) ?: throw AgentRunNotFoundException(runId)
        getSession(run.sessionId, requestedBy)
        return run
    }

    fun getProposal(proposalId: String, requestedBy: Int): AgentWorkflowProposal {
        val proposal = repository.findProposal(proposalId) ?: throw AgentProposalNotFoundException(proposalId)
        getRun(proposal.runId, requestedBy)
        return proposal
    }

    fun getRunProposals(runId: String, requestedBy: Int): List<AgentWorkflowProposal> {
        getRun(runId, requestedBy)
        return repository.findProposalsByRun(runId)
    }

    fun getSessionRuns(sessionId: String, requestedBy: Int): List<AgentRun> {
        getSession(sessionId, requestedBy)
        return repository.findRunsBySession(sessionId)
    }

    fun getSessionMessages(sessionId: String, requestedBy: Int): List<AgentMessageRecord> {
        getSession(sessionId, requestedBy)
        return chatMemory?.messages(sessionId) ?: emptyList()
    }

    fun getTokenQuota(projectId: Int, requestedBy: Int): AgentTokenQuotaSnapshot {
        authorization.requireReadable(requestedBy, projectId)
        return requireNotNull(tokenQuota) { "Agent Token quota service is not configured." }.snapshot(requestedBy, projectId)
    }

    suspend fun cancelRun(runId: String, requestedBy: Int): AgentRun {
        val run = getRun(runId, requestedBy)
        if (run.status in TERMINAL_STATUSES) return run
        val session = getSession(run.sessionId, requestedBy)
        authorization.requireWritable(requestedBy, session.projectId)
        val cancelled = repository.updateRun(run.transitionTo(AgentRunStatus.CANCELLED, Instant.now(clock)))
        val context = AgentExecutionContext(session.id, run.id, requestedBy, session.projectId, run.correlationId)
        audit.record(context, AgentAuditEventType.RUN_CANCELLED, cancelled.status)
        eventPublisher.publish(context.event(AgentAuditEventType.RUN_CANCELLED, cancelled.status, at = Instant.now(clock)))
        return cancelled
    }

    fun getRunAudit(runId: String, requestedBy: Int): List<AgentAuditEvent> {
        getRun(runId, requestedBy)
        return repository.findAuditByRun(runId)
    }

    suspend fun plan(
        sessionId: String,
        requestedBy: Int,
        definition: AgentDefinition,
        policy: AgentPolicy,
        userIntent: String,
        maxRepairAttempts: Int = 2,
        onRunStarted: suspend (AgentRun) -> Unit = {}
    ): AgentPlanningResult {
        val session = requireSession(sessionId)
        requireSameUser(session, requestedBy)
        authorization.requireReadable(requestedBy, session.projectId)
        require(session.status == AgentSessionStatus.ACTIVE) { "Agent session is closed: $sessionId" }
        require(session.agentId == definition.id && session.agentVersion == definition.version) {
            "Agent session definition does not match ${definition.id}@${definition.version}."
        }
        val inspection = promptSecurity.inspect(userIntent)
        if (!inspection.allowed) {
            audit.recordSession(session, AgentAuditEventType.PROMPT_REJECTED, buildJsonObject {
                put("rule", requireNotNull(inspection.matchedRule))
                put("fingerprint", inspection.fingerprint)
                put("promptLength", userIntent.length)
            })
            throw AgentPromptInjectionRejectedException(
                requireNotNull(inspection.matchedRule), inspection.fingerprint, userIntent.length
            )
        }
        val safeIntent = inspection.sanitizedText

        var run = lifecycleFactory.createRun(session, definition.modelProfileId, correlationIdSupplier())
        repository.saveRun(run)
        val context = AgentExecutionContext(session.id, run.id, session.userId, session.projectId, run.correlationId)
        val conversationHistory = chatMemory?.recall(session.id).orEmpty()
        chatMemory?.rememberUser(context, safeIntent, Instant.now(clock))
        audit.record(context, AgentAuditEventType.RUN_CREATED, run.status)
        eventPublisher.publish(context.event(AgentAuditEventType.RUN_CREATED, run.status, at = Instant.now(clock)))
        run = repository.updateRun(run.transitionTo(AgentRunStatus.PLANNING, Instant.now(clock)))
        audit.record(context, AgentAuditEventType.RUN_STARTED, run.status)
        eventPublisher.publish(context.event(AgentAuditEventType.RUN_STARTED, run.status, at = Instant.now(clock)))
        onRunStarted(run)

        return try {
            val proposal = planner.plan(
                WorkflowPlanningCommand(
                    context, definition, policy, safeIntent, maxRepairAttempts,
                    modelProfileId = run.modelProfileId,
                    conversationHistory = conversationHistory
                )
            )
            run = requireNotNull(repository.findRun(run.id))
                .transitionTo(AgentRunStatus.VALIDATING, Instant.now(clock))
            repository.updateRun(run)
            repository.saveProposal(proposal)
            chatMemory?.rememberAssistant(context, proposal, Instant.now(clock))
            audit.record(context, AgentAuditEventType.PROPOSAL_CREATED, run.status, buildJsonObject {
                put("proposalId", proposal.id); put("approvalRequired", proposal.approvalRequired)
            })
            run = repository.updateRun(run.transitionTo(AgentRunStatus.COMPLETED, Instant.now(clock)))
            audit.record(context, AgentAuditEventType.RUN_COMPLETED, run.status)
            eventPublisher.publish(context.event(AgentAuditEventType.RUN_COMPLETED, run.status, at = Instant.now(clock)))
            AgentPlanningResult(session, run, proposal)
        } catch (cancelled: CancellationException) {
            val current = repository.findRun(run.id) ?: run
            if (current.status !in TERMINAL_STATUSES) {
                val stopped = repository.updateRun(current.transitionTo(AgentRunStatus.CANCELLED, Instant.now(clock)))
                audit.record(context, AgentAuditEventType.RUN_CANCELLED, stopped.status)
                eventPublisher.publish(context.event(AgentAuditEventType.RUN_CANCELLED, stopped.status, at = Instant.now(clock)))
            }
            throw cancelled
        } catch (failure: Throwable) {
            val code = (failure as? WorkflowPlanningException)?.code ?: "AGENT_PLANNING_FAILED"
            val safeFailureMessage = redactor.redact(failure.message ?: "Agent planning failed.")
            val current = repository.findRun(run.id) ?: run
            if (current.status !in TERMINAL_STATUSES) {
                val failed = current.transitionTo(
                    AgentRunStatus.FAILED,
                    Instant.now(clock),
                    AgentRunFailure(code, safeFailureMessage, retryable = false)
                )
                repository.updateRun(failed)
                audit.record(context, AgentAuditEventType.RUN_FAILED, failed.status, buildJsonObject {
                    put("code", code); put("message", failed.failure!!.message)
                })
                eventPublisher.publish(context.event(AgentAuditEventType.RUN_FAILED, failed.status, buildJsonObject {
                    put("code", code); put("message", requireNotNull(failed.failure).message)
                }, Instant.now(clock)))
            }
            throw failure
        }
    }

    private fun requireSession(id: String): AgentSession =
        repository.findSession(id) ?: throw AgentSessionNotFoundException(id)

    private fun requireSameUser(session: AgentSession, requestedBy: Int) {
        if (session.userId != requestedBy) throw AgentSessionAccessDeniedException(session.id)
    }

    companion object {
        private val TERMINAL_STATUSES = setOf(AgentRunStatus.COMPLETED, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED)
    }
}

class AgentSessionNotFoundException(id: String) : EDBException("AGENT_SESSION_NOT_FOUND", "Agent session was not found: $id")
class AgentSessionAccessDeniedException(id: String) : EDBException(
    "PERMISSION_DENIED",
    "Agent session does not belong to the requesting user: $id"
)
class AgentRunNotFoundException(id: String) : EDBException("AGENT_RUN_NOT_FOUND", "Agent run was not found: $id")
class AgentProposalNotFoundException(id: String) : EDBException("AGENT_PROPOSAL_NOT_FOUND", "Agent proposal was not found: $id")

private fun AgentExecutionContext.event(
    type: AgentAuditEventType,
    status: AgentRunStatus? = null,
    details: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
    at: Instant
) = AgentLifecycleEvent(sessionId, runId, userId, projectId, type, status, details, at)

private fun AgentSession.event(type: AgentAuditEventType) = AgentLifecycleEvent(
    id, null, userId, projectId, type, occurredAt = updatedAt
)
