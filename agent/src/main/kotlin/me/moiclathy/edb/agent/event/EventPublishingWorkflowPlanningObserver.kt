package me.moiclathy.edb.agent.event

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import me.moiclathy.edb.agent.persistence.AgentAuditEventType
import me.moiclathy.edb.agent.planning.WorkflowPlanningObserver
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.provider.AgentModelRequest
import me.moiclathy.edb.agent.provider.AgentModelResponse
import me.moiclathy.edb.agent.provider.AgentRequestedToolCall
import me.moiclathy.edb.agent.tools.WorkflowPlanningValidationResult
import java.time.Instant
import me.moiclathy.edb.agent.policy.AgentSensitiveDataRedactor

class EventPublishingWorkflowPlanningObserver(
    private val publisher: AgentEventPublisher,
    private val redactor: AgentSensitiveDataRedactor = AgentSensitiveDataRedactor()
) : WorkflowPlanningObserver {
    override suspend fun modelRequested(context: AgentExecutionContext, index: Int, request: AgentModelRequest, at: Instant) =
        publish(context, AgentAuditEventType.MODEL_REQUESTED, at, buildJsonObject {
            put("index", index); put("requestId", request.requestId)
        })

    override suspend fun modelTextDelta(
        context: AgentExecutionContext,
        index: Int,
        sequence: Long,
        text: String,
        at: Instant
    ) = publish(context, AgentAuditEventType.MESSAGE_DELTA, at, buildJsonObject {
        put("index", index); put("sequence", sequence); put("text", redactor.redact(text))
    })

    override suspend fun modelResponded(context: AgentExecutionContext, index: Int, response: AgentModelResponse, at: Instant) {
        publish(context, AgentAuditEventType.MODEL_RESPONDED, at, buildJsonObject {
            put("index", index); put("finishReason", response.finishReason.name); put("toolCallCount", response.toolCalls.size)
        })
    }

    override suspend fun modelFailed(context: AgentExecutionContext, index: Int, failure: Throwable, at: Instant) =
        publish(context, AgentAuditEventType.MODEL_FAILED, at, buildJsonObject {
            put("index", index); put("message", redactor.redact(failure.message ?: "Agent model failed."))
        })

    override suspend fun toolStarted(context: AgentExecutionContext, call: AgentRequestedToolCall, capability: AgentCapability, at: Instant) =
        publish(context, AgentAuditEventType.TOOL_STARTED, at, buildJsonObject {
            put("toolCallId", call.id); put("toolName", call.name); put("capability", capability.name)
        })

    override suspend fun toolCompleted(context: AgentExecutionContext, call: AgentRequestedToolCall, result: JsonObject, at: Instant) =
        publish(
            context,
            if ((result["ok"]?.toString()?.toBooleanStrictOrNull()) == false) AgentAuditEventType.TOOL_FAILED else AgentAuditEventType.TOOL_COMPLETED,
            at,
            buildJsonObject { put("toolCallId", call.id); put("toolName", call.name) }
        )

    override suspend fun validationFailed(context: AgentExecutionContext, result: WorkflowPlanningValidationResult, at: Instant) =
        publish(context, AgentAuditEventType.VALIDATION_FAILED, at, buildJsonObject {
            put("errors", buildJsonArray { result.errors.forEach { error -> add(buildJsonObject {
                put("path", error.path); put("message", error.message)
            }) } })
        })

    override suspend fun repairRequested(context: AgentExecutionContext, attempt: Int, reason: String, at: Instant) =
        publish(context, AgentAuditEventType.REPAIR_REQUESTED, at, buildJsonObject {
            put("attempt", attempt); put("reason", reason)
        })

    override suspend fun proposalCreated(context: AgentExecutionContext, proposal: AgentWorkflowProposal, at: Instant) =
        publish(context, AgentAuditEventType.PROPOSAL_CREATED, at, buildJsonObject { put("proposalId", proposal.id) })

    private suspend fun publish(
        context: AgentExecutionContext,
        type: AgentAuditEventType,
        at: Instant,
        details: JsonObject
    ) = publisher.publish(
        AgentLifecycleEvent(
            sessionId = context.sessionId,
            runId = context.runId,
            actorUserId = context.userId,
            projectId = context.projectId,
            type = type,
            details = redactor.redact(details),
            occurredAt = at
        )
    )
}
