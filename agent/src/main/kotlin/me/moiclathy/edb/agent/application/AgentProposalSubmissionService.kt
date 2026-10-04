package me.moiclathy.edb.agent.application

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.audit.AgentAuditService
import me.moiclathy.edb.agent.event.AgentEventPublisher
import me.moiclathy.edb.agent.event.AgentLifecycleEvent
import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentProposalDecisionStatus
import me.moiclathy.edb.agent.persistence.AgentAuditEventType
import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.workflow.definition.ProjectWorkflowDefinition
import me.moiclathy.edb.core.task.workflow.definition.ProjectWorkflowDefinitionRepository
import me.moiclathy.edb.core.task.workflow.definition.ProjectWorkflowDefinitionService
import me.moiclathy.edb.core.task.workflow.definition.SubmitWorkflowDefinitionTaskCommand
import me.moiclathy.edb.core.task.workflow.definition.WorkflowDefinitionTaskApplicationService
import me.moiclathy.edb.core.task.workflow.definition.WorkflowDefinitionTaskPayloadCodec
import java.time.Clock
import java.time.Instant

data class AgentProposalSubmission(val proposalId: String, val definition: ProjectWorkflowDefinition, val task: Task)

class AgentProposalSubmissionService(
    private val planning: AgentPlanningApplicationService,
    private val approvals: AgentProposalApprovalService,
    private val definitionService: ProjectWorkflowDefinitionService,
    private val definitions: ProjectWorkflowDefinitionRepository,
    private val taskService: WorkflowDefinitionTaskApplicationService,
    private val tasks: TaskRepository,
    private val audit: AgentAuditService,
    private val eventPublisher: AgentEventPublisher = AgentEventPublisher.None,
    private val clock: Clock = Clock.systemUTC()
) {
    fun getSubmission(proposalId: String, requestedBy: Int): AgentProposalSubmission? {
        planning.getProposal(proposalId, requestedBy)
        val task = tasks.findByIdempotencyKey(proposalId.idempotencyKey()) ?: return null
        val payload = WorkflowDefinitionTaskPayloadCodec.decode(task.configSnapshot)
        val definition = definitions.findById(payload.definitionId) ?: return null
        return AgentProposalSubmission(proposalId, definition, task)
    }

    suspend fun submit(proposalId: String, requestedBy: Int, context: Map<String, String> = emptyMap()): AgentProposalSubmission {
        getSubmission(proposalId, requestedBy)?.let { return it }
        val proposal = planning.getProposal(proposalId, requestedBy)
        val run = planning.getRun(proposal.runId, requestedBy)
        val session = planning.getSession(run.sessionId, requestedBy)
        val decision = approvals.getDecision(proposalId, requestedBy)
            ?: throw AgentProposalNotApprovedException(proposalId)
        if (decision.status != AgentProposalDecisionStatus.APPROVED) throw AgentProposalNotApprovedException(proposalId)
        val definition = definitionService.create(
            session.projectId, proposal.workflowIrJson, requestedBy, "agent-proposal:$proposalId"
        )
        if (definition.workflowSha256 != decision.workflowSha256) throw AgentProposalContentChangedException(proposalId)
        val outcome = taskService.submitWithOutcome(SubmitWorkflowDefinitionTaskCommand(
            definitionId = requireNotNull(definition.id), projectId = session.projectId,
            expectedVersion = definition.version, expectedSha256 = definition.workflowSha256,
            context = context, requestedBy = requestedBy, idempotencyKey = proposalId.idempotencyKey()
        ))
        val task = outcome.task
        val executionContext = AgentExecutionContext(session.id, run.id, requestedBy, session.projectId, run.correlationId)
        val now = Instant.now(clock)
        if (outcome.created) {
            publish(executionContext, AgentAuditEventType.WORKFLOW_DEFINITION_CREATED, buildJsonObject {
                put("proposalId", proposalId); put("definitionId", definition.id!!); put("definitionVersion", definition.version)
                put("workflowSha256", definition.workflowSha256)
            }, now)
            publish(executionContext, AgentAuditEventType.TASK_SUBMITTED, buildJsonObject {
                put("proposalId", proposalId); put("definitionId", definition.id!!); put("taskId", task.id!!)
            }, now)
        }
        return AgentProposalSubmission(proposalId, definition, task)
    }

    private suspend fun publish(context: AgentExecutionContext, type: AgentAuditEventType, details: kotlinx.serialization.json.JsonObject, at: Instant) {
        audit.record(context, type, details = details, at = at)
        eventPublisher.publish(AgentLifecycleEvent(context.sessionId, context.runId, context.userId, context.projectId,
            type, details = details, occurredAt = at))
    }
}

class AgentProposalNotApprovedException(id: String) :
    EDBException("AGENT_PROPOSAL_NOT_APPROVED", "Agent proposal is not approved: $id")
class AgentProposalContentChangedException(id: String) :
    EDBException("AGENT_PROPOSAL_CONTENT_CHANGED", "Agent proposal content no longer matches its approval: $id")

private fun String.idempotencyKey() = "agent-proposal:$this"
