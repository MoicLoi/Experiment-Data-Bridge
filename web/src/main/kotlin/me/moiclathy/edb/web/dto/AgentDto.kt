package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.agent.application.AgentPlanningResult
import me.moiclathy.edb.agent.application.AgentProposalSubmission
import me.moiclathy.edb.agent.model.AgentRun
import me.moiclathy.edb.agent.model.AgentProposalDecision
import me.moiclathy.edb.agent.model.AgentSession
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import me.moiclathy.edb.agent.persistence.AgentAuditEvent
import me.moiclathy.edb.agent.persistence.AgentMessageRecord
import me.moiclathy.edb.agent.application.AgentMetricsSnapshot
import me.moiclathy.edb.agent.application.AgentRetentionCleanupResult
import me.moiclathy.edb.agent.application.AgentRetentionEstimate
import me.moiclathy.edb.agent.application.AgentRetentionPolicy
import me.moiclathy.edb.agent.provider.AgentProviderProfileStatus
import me.moiclathy.edb.agent.application.AgentTokenQuotaSnapshot

@Serializable
data class CreateAgentSessionRequest(
    val agentId: String = "workflow-planner",
    val agentVersion: String = "1"
)

@Serializable
data class PlanAgentWorkflowRequest(
    val message: String,
    val maxRepairAttempts: Int = 2,
    val modelProfileId: String? = null
)

@Serializable
data class AgentModelProfileCatalogResponse(
    val defaultProfileId: String,
    val items: List<AgentModelProfileResponse>
)

@Serializable
data class AgentModelProfileResponse(
    val id: String,
    val providerId: String,
    val modelName: String,
    val toolCallingEnabled: Boolean,
    val structuredOutputEnabled: Boolean,
    val builtIn: Boolean,
    val default: Boolean
)

@Serializable
data class AgentProviderStatusListResponse(val items: List<AgentProviderStatusResponse>)

@Serializable
data class AgentProviderStatusResponse(
    val id: String,
    val providerId: String,
    val modelName: String,
    val builtIn: Boolean,
    val initialized: Boolean,
    val apiKeyConfigured: Boolean,
    val customEndpointConfigured: Boolean,
    val health: String,
    val consecutiveFailures: Int,
    val circuitOpenUntil: String?
) {
    companion object { fun from(status: AgentProviderProfileStatus) = AgentProviderStatusResponse(
        status.id, status.providerId, status.modelName, status.builtIn, status.initialized,
        status.apiKeyConfigured, status.customEndpointConfigured, status.health.name,
        status.consecutiveFailures, status.circuitOpenUntil?.toString()
    ) }
}

@Serializable
data class AgentModelMetricsListResponse(val items: List<AgentModelMetricsResponse>)

@Serializable
data class AgentModelMetricsResponse(
    val profileId: String,
    val requests: Long,
    val successes: Long,
    val failures: Long,
    val fallbackUses: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val inFlight: Long,
    val averageLatencyMillis: Double?,
    val lastCalledAt: String?
) {
    companion object { fun from(status: AgentProviderProfileStatus) = status.metrics.let { metrics ->
        AgentModelMetricsResponse(
            status.id, metrics.requests, metrics.successes, metrics.failures, metrics.fallbackUses,
            metrics.inputTokens, metrics.outputTokens, metrics.inFlight,
            metrics.averageLatencyMillis, metrics.lastCalledAt?.toString()
        )
    } }
}

@Serializable
data class AgentTokenQuotaResponse(
    val enabled: Boolean,
    val date: String,
    val items: List<AgentTokenQuotaUsageResponse>
) {
    companion object { fun from(snapshot: AgentTokenQuotaSnapshot) = AgentTokenQuotaResponse(
        snapshot.enabled, snapshot.date.toString(), snapshot.usages.map { usage ->
            AgentTokenQuotaUsageResponse(
                usage.key.scope.name, usage.key.id, usage.usedTokens, usage.limit,
                (usage.limit - usage.usedTokens).coerceAtLeast(0)
            )
        }
    ) }
}

@Serializable
data class AgentTokenQuotaUsageResponse(
    val scope: String,
    val scopeId: Int,
    val usedTokens: Long,
    val limit: Long,
    val remainingTokens: Long
)

@Serializable
data class AgentSessionMessageListResponse(val items: List<AgentSessionMessageResponse>)

@Serializable
data class AgentSessionMessageResponse(
    val id: Long,
    val runId: String,
    val role: String,
    val content: String,
    val createdAt: String
) {
    companion object {
        fun from(message: AgentMessageRecord) = AgentSessionMessageResponse(
            requireNotNull(message.id), message.runId, message.role.name, message.content, message.createdAt.toString()
        )
    }
}

@Serializable
data class AgentMetricsResponse(
    val totalRuns: Long, val received: Long, val planning: Long, val validating: Long,
    val awaitingApproval: Long, val executing: Long, val waitingTask: Long,
    val completed: Long, val failed: Long, val cancelled: Long,
    val inputTokens: Long, val outputTokens: Long, val averageExecutionMillis: Double?, val collectedAt: String
) {
    companion object { fun from(snapshot: AgentMetricsSnapshot) = snapshot.metrics.let {
        AgentMetricsResponse(it.totalRuns, it.received, it.planning, it.validating,
            it.awaitingApproval, it.executing, it.waitingTask, it.completed, it.failed,
            it.cancelled, it.inputTokens, it.outputTokens, it.averageExecutionMillis, snapshot.collectedAt.toString())
    } }
}

@Serializable
data class AgentRetentionPolicyResponse(
    val enabled: Boolean,
    val retentionDays: Long,
    val cleanupIntervalMinutes: Long,
    val initialDelayMinutes: Long,
    val batchSize: Int,
    val maxBatches: Int
) {
    companion object { fun from(policy: AgentRetentionPolicy) = AgentRetentionPolicyResponse(
        policy.enabled, policy.retentionPeriod.toDays(), policy.cleanupInterval.toMinutes(),
        policy.initialDelay.toMinutes(), policy.batchSize, policy.maxBatches
    ) }
}

@Serializable
data class AgentRetentionEstimateResponse(val cutoff: String, val expiredRuns: Long, val collectedAt: String) {
    companion object { fun from(value: AgentRetentionEstimate) = AgentRetentionEstimateResponse(
        value.cutoff.toString(), value.expiredRuns, value.collectedAt.toString()
    ) }
}

@Serializable
data class AgentRetentionCleanupResponse(val cutoff: String, val deletedRuns: Int, val batches: Int, val completedAt: String) {
    companion object { fun from(value: AgentRetentionCleanupResult) = AgentRetentionCleanupResponse(
        value.cutoff.toString(), value.deletedRuns, value.batches, value.completedAt.toString()
    ) }
}

@Serializable
data class AgentRetentionOverviewResponse(
    val policy: AgentRetentionPolicyResponse,
    val lastResult: AgentRetentionCleanupResponse?,
    val started: Boolean,
    val running: Boolean,
    val lastStartedAt: String?,
    val lastFailure: String?
)

@Serializable
data class RejectAgentProposalRequest(val reason: String)

@Serializable
data class SubmitAgentProposalRequest(val context: Map<String, String> = emptyMap())

@Serializable
data class AgentProposalSubmissionResponse(
    val proposalId: String,
    val definitionId: Long,
    val workflowId: String,
    val definitionVersion: Int,
    val workflowSha256: String,
    val task: TaskResponse
) {
    companion object {
        fun from(submission: AgentProposalSubmission) = AgentProposalSubmissionResponse(
            submission.proposalId, requireNotNull(submission.definition.id), submission.definition.workflowId,
            submission.definition.version, submission.definition.workflowSha256, TaskResponse.from(submission.task)
        )
    }
}

@Serializable
data class AgentProposalDecisionResponse(
    val proposalId: String,
    val status: String,
    val decidedBy: Int,
    val workflowSha256: String,
    val reason: String?,
    val decidedAt: String
) {
    companion object {
        fun from(decision: AgentProposalDecision) = AgentProposalDecisionResponse(
            decision.proposalId, decision.status.name, decision.decidedBy,
            decision.workflowSha256, decision.reason, decision.decidedAt.toString()
        )
    }
}

@Serializable
data class AgentSessionResponse(
    val id: String,
    val userId: Int,
    val projectId: Int,
    val agentId: String,
    val agentVersion: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(session: AgentSession) = AgentSessionResponse(
            session.id, session.userId, session.projectId, session.agentId, session.agentVersion,
            session.status.name, session.createdAt.toString(), session.updatedAt.toString()
        )
    }
}

@Serializable
data class AgentRunResponse(
    val id: String,
    val sessionId: String,
    val correlationId: String,
    val status: String,
    val modelProfileId: String,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val failureCode: String?,
    val failureMessage: String?,
    val failureRetryable: Boolean?,
    val startedAt: String,
    val updatedAt: String,
    val completedAt: String?
) {
    companion object {
        fun from(run: AgentRun) = AgentRunResponse(
            run.id, run.sessionId, run.correlationId, run.status.name, run.modelProfileId,
            run.tokenUsage?.inputTokens, run.tokenUsage?.outputTokens,
            run.failure?.code, run.failure?.message, run.failure?.retryable,
            run.startedAt.toString(), run.updatedAt.toString(), run.completedAt?.toString()
        )
    }
}

@Serializable
data class AgentWorkflowProposalResponse(
    val id: String,
    val runId: String,
    val workflowIrJson: String,
    val explanation: String,
    val assumptions: List<String>,
    val warnings: List<String>,
    val valid: Boolean,
    val executionOrder: List<String>,
    val validationErrors: List<AgentValidationErrorResponse>,
    val approvalRequired: Boolean,
    val createdAt: String
) {
    companion object {
        fun from(proposal: AgentWorkflowProposal) = AgentWorkflowProposalResponse(
            proposal.id, proposal.runId, proposal.workflowIrJson, proposal.explanation,
            proposal.assumptions, proposal.warnings, proposal.validation.valid,
            proposal.validation.executionOrder,
            proposal.validation.errors.map { AgentValidationErrorResponse(it.path, it.message) },
            proposal.approvalRequired, proposal.createdAt.toString()
        )
    }
}

@Serializable
data class AgentValidationErrorResponse(val path: String, val message: String)

@Serializable
data class AgentPlanningResponse(
    val session: AgentSessionResponse,
    val run: AgentRunResponse,
    val proposal: AgentWorkflowProposalResponse
) {
    companion object {
        fun from(result: AgentPlanningResult) = AgentPlanningResponse(
            AgentSessionResponse.from(result.session),
            AgentRunResponse.from(result.run),
            AgentWorkflowProposalResponse.from(result.proposal)
        )
    }
}

@Serializable
data class AgentRunAcceptedResponse(val run: AgentRunResponse) {
    companion object { fun from(run: AgentRun) = AgentRunAcceptedResponse(AgentRunResponse.from(run)) }
}

@Serializable
data class AgentProposalListResponse(val items: List<AgentWorkflowProposalResponse>)

@Serializable
data class AgentAuditEventResponse(
    val id: Long,
    val sessionId: String,
    val runId: String?,
    val actorUserId: Int,
    val projectId: Int,
    val type: String,
    val runStatus: String?,
    val detailsJson: String,
    val createdAt: String
) {
    companion object {
        fun from(event: AgentAuditEvent) = AgentAuditEventResponse(
            requireNotNull(event.id), event.sessionId, event.runId, event.actorUserId, event.projectId,
            event.type.name, event.runStatus?.name, event.details.toString(), event.createdAt.toString()
        )
    }
}

@Serializable
data class AgentAuditTimelineResponse(val runId: String, val events: List<AgentAuditEventResponse>)

@Serializable
data class AgentLifecycleStreamEvent(
    val sessionId: String,
    val runId: String?,
    val projectId: Int,
    val type: String,
    val runStatus: String?,
    val detailsJson: String,
    val occurredAt: String
)
