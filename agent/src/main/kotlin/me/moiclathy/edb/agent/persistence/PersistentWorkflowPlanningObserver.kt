package me.moiclathy.edb.agent.persistence

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.audit.AgentAuditService
import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentTokenUsage
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import me.moiclathy.edb.agent.planning.WorkflowPlanningObserver
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.provider.AgentChatMessage
import me.moiclathy.edb.agent.provider.AgentChatRole
import me.moiclathy.edb.agent.provider.AgentModelRequest
import me.moiclathy.edb.agent.provider.AgentModelResponse
import me.moiclathy.edb.agent.provider.AgentRequestedToolCall
import me.moiclathy.edb.agent.tools.WorkflowPlanningValidationResult
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import me.moiclathy.edb.agent.policy.AgentSensitiveDataRedactor

class PersistentWorkflowPlanningObserver(
    private val repository: AgentRepository,
    private val audit: AgentAuditService,
    private val redactor: AgentSensitiveDataRedactor = AgentSensitiveDataRedactor()
) : WorkflowPlanningObserver {
    private val activeToolCalls = ConcurrentHashMap<String, AgentToolCallRecord>()

    override suspend fun messageAdded(context: AgentExecutionContext, message: AgentChatMessage, at: Instant) {
        repository.appendMessage(message.copy(content = redactor.redact(message.content)).record(context, at))
    }

    override suspend fun modelRequested(context: AgentExecutionContext, index: Int, request: AgentModelRequest, at: Instant) {
        audit.record(context, AgentAuditEventType.MODEL_REQUESTED, details = buildJsonObject {
            put("index", index); put("requestId", request.requestId); put("messageCount", request.messages.size)
            put("toolCount", request.tools.size); put("structuredOutputRequested", request.responseSchema != null)
        }, at = at)
    }

    override suspend fun modelResponded(context: AgentExecutionContext, index: Int, response: AgentModelResponse, at: Instant) {
        val assistantContent = response.text ?: response.structuredOutput?.toString()
            ?: "Requested tools: ${response.toolCalls.joinToString { "${it.name}#${it.id}" }}"
        repository.appendMessage(
            AgentMessageRecord(
                sessionId = context.sessionId,
                runId = context.runId,
                role = AgentChatRole.ASSISTANT,
                content = redactor.redact(assistantContent),
                createdAt = at
            )
        )
        response.tokenUsage?.let { usage ->
            repository.findRun(context.runId)?.let { run ->
                val previous = run.tokenUsage ?: AgentTokenUsage(0, 0)
                repository.updateRun(
                    run.withTokenUsage(
                        AgentTokenUsage(
                            Math.addExact(previous.inputTokens, usage.inputTokens),
                            Math.addExact(previous.outputTokens, usage.outputTokens)
                        ),
                        at
                    )
                )
            }
        }
        audit.record(context, AgentAuditEventType.MODEL_RESPONDED, details = buildJsonObject {
            put("index", index); put("finishReason", response.finishReason.name)
            put("toolCallCount", response.toolCalls.size); put("structuredOutput", response.structuredOutput != null)
            response.tokenUsage?.let { put("inputTokens", it.inputTokens); put("outputTokens", it.outputTokens) }
        }, at = at)
    }

    override suspend fun modelFailed(context: AgentExecutionContext, index: Int, failure: Throwable, at: Instant) {
        audit.record(context, AgentAuditEventType.MODEL_FAILED, details = buildJsonObject {
            put("index", index); put("errorType", failure::class.qualifiedName ?: "unknown")
            put("message", redactor.redact(failure.message ?: "Agent model invocation failed."))
        }, at = at)
    }

    override suspend fun toolStarted(context: AgentExecutionContext, call: AgentRequestedToolCall, capability: AgentCapability, at: Instant) {
        val persisted = repository.saveToolCall(
            AgentToolCallRecord(
                runId = context.runId,
                toolCallId = call.id,
                toolName = call.name,
                capability = capability,
                arguments = call.arguments,
                startedAt = at
            )
        )
        activeToolCalls[key(context.runId, call.id)] = persisted
        audit.record(context, AgentAuditEventType.TOOL_STARTED, details = buildJsonObject {
            put("toolCallId", call.id); put("toolName", call.name); put("capability", capability.name)
        }, at = at)
    }

    override suspend fun toolCompleted(context: AgentExecutionContext, call: AgentRequestedToolCall, result: JsonObject, at: Instant) {
        val started = activeToolCalls.remove(key(context.runId, call.id))
            ?: error("Agent tool call was not started: ${call.id}")
        val successful = (result["ok"] as? JsonPrimitive)?.booleanOrNull != false
        val errorMessage = (result["error"] as? JsonPrimitive)?.content
        val errorCode = (result["code"] as? JsonPrimitive)?.content
        repository.updateToolCall(
            started.copy(
                result = result,
                status = if (successful) AgentToolCallStatus.COMPLETED else AgentToolCallStatus.FAILED,
                errorCode = errorCode,
                errorMessage = errorMessage.takeIf { !successful } ?: "Tool returned an unsuccessful result.".takeIf { !successful },
                completedAt = at
            )
        )
        audit.record(
            context,
            if (successful) AgentAuditEventType.TOOL_COMPLETED else AgentAuditEventType.TOOL_FAILED,
            details = buildJsonObject {
                put("toolCallId", call.id); put("toolName", call.name)
                errorCode?.let { put("errorCode", it) }; errorMessage?.let { put("errorMessage", it) }
            },
            at = at
        )
    }

    override suspend fun validationFailed(context: AgentExecutionContext, result: WorkflowPlanningValidationResult, at: Instant) {
        audit.record(context, AgentAuditEventType.VALIDATION_FAILED, details = buildJsonObject {
            put("errors", buildJsonArray { result.errors.forEach { error -> add(buildJsonObject {
                put("path", error.path); put("message", error.message)
            }) } })
        }, at = at)
    }

    override suspend fun repairRequested(context: AgentExecutionContext, attempt: Int, reason: String, at: Instant) {
        audit.record(context, AgentAuditEventType.REPAIR_REQUESTED, details = buildJsonObject {
            put("attempt", attempt); put("reason", reason)
        }, at = at)
    }

    override suspend fun proposalCreated(context: AgentExecutionContext, proposal: AgentWorkflowProposal, at: Instant) = Unit

    private fun key(runId: String, toolCallId: String) = "$runId\u0000$toolCallId"
}

private fun AgentChatMessage.record(context: AgentExecutionContext, at: Instant) = AgentMessageRecord(
    sessionId = context.sessionId,
    runId = context.runId,
    role = role,
    content = content,
    toolCallId = toolCallId,
    createdAt = at
)
