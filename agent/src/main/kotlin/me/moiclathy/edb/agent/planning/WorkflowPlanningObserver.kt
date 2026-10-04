package me.moiclathy.edb.agent.planning

import kotlinx.serialization.json.JsonObject
import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.provider.AgentChatMessage
import me.moiclathy.edb.agent.provider.AgentModelRequest
import me.moiclathy.edb.agent.provider.AgentModelResponse
import me.moiclathy.edb.agent.provider.AgentRequestedToolCall
import me.moiclathy.edb.agent.tools.WorkflowPlanningValidationResult
import java.time.Instant

interface WorkflowPlanningObserver {
    suspend fun messageAdded(context: AgentExecutionContext, message: AgentChatMessage, at: Instant) = Unit
    suspend fun modelRequested(context: AgentExecutionContext, index: Int, request: AgentModelRequest, at: Instant) = Unit
    suspend fun modelTextDelta(context: AgentExecutionContext, index: Int, sequence: Long, text: String, at: Instant) = Unit
    suspend fun modelResponded(context: AgentExecutionContext, index: Int, response: AgentModelResponse, at: Instant) = Unit
    suspend fun modelFailed(context: AgentExecutionContext, index: Int, failure: Throwable, at: Instant) = Unit
    suspend fun toolStarted(context: AgentExecutionContext, call: AgentRequestedToolCall, capability: AgentCapability, at: Instant) = Unit
    suspend fun toolCompleted(context: AgentExecutionContext, call: AgentRequestedToolCall, result: JsonObject, at: Instant) = Unit
    suspend fun validationFailed(context: AgentExecutionContext, result: WorkflowPlanningValidationResult, at: Instant) = Unit
    suspend fun repairRequested(context: AgentExecutionContext, attempt: Int, reason: String, at: Instant) = Unit
    suspend fun proposalCreated(context: AgentExecutionContext, proposal: AgentWorkflowProposal, at: Instant) = Unit

    data object None : WorkflowPlanningObserver
}

class CompositeWorkflowPlanningObserver(
    private val observers: List<WorkflowPlanningObserver>
) : WorkflowPlanningObserver {
    override suspend fun messageAdded(context: AgentExecutionContext, message: AgentChatMessage, at: Instant) =
        observers.forEach { it.messageAdded(context, message, at) }
    override suspend fun modelRequested(context: AgentExecutionContext, index: Int, request: AgentModelRequest, at: Instant) =
        observers.forEach { it.modelRequested(context, index, request, at) }
    override suspend fun modelTextDelta(context: AgentExecutionContext, index: Int, sequence: Long, text: String, at: Instant) =
        observers.forEach { it.modelTextDelta(context, index, sequence, text, at) }
    override suspend fun modelResponded(context: AgentExecutionContext, index: Int, response: AgentModelResponse, at: Instant) =
        observers.forEach { it.modelResponded(context, index, response, at) }
    override suspend fun modelFailed(context: AgentExecutionContext, index: Int, failure: Throwable, at: Instant) =
        observers.forEach { it.modelFailed(context, index, failure, at) }
    override suspend fun toolStarted(context: AgentExecutionContext, call: AgentRequestedToolCall, capability: AgentCapability, at: Instant) =
        observers.forEach { it.toolStarted(context, call, capability, at) }
    override suspend fun toolCompleted(context: AgentExecutionContext, call: AgentRequestedToolCall, result: JsonObject, at: Instant) =
        observers.forEach { it.toolCompleted(context, call, result, at) }
    override suspend fun validationFailed(context: AgentExecutionContext, result: WorkflowPlanningValidationResult, at: Instant) =
        observers.forEach { it.validationFailed(context, result, at) }
    override suspend fun repairRequested(context: AgentExecutionContext, attempt: Int, reason: String, at: Instant) =
        observers.forEach { it.repairRequested(context, attempt, reason, at) }
    override suspend fun proposalCreated(context: AgentExecutionContext, proposal: AgentWorkflowProposal, at: Instant) =
        observers.forEach { it.proposalCreated(context, proposal, at) }
}
