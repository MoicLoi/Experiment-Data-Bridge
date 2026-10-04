package me.moiclathy.edb.agent.provider

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.model.AgentTokenUsage
import me.moiclathy.edb.agent.tools.WorkflowPlanningTools

/** Deterministic development model; never performs network I/O and must not be enabled in production. */
class FakeWorkflowPlanningChatModel : AgentChatModel {
    override suspend fun generate(request: AgentModelRequest): AgentModelResponse {
        val toolMessages = request.messages.filter { it.role == AgentChatRole.TOOL }
        if (toolMessages.none { it.content.contains("\"steps\"") }) {
            return toolCall(request, "catalog", WorkflowPlanningTools.LIST_STEPS, JsonObject(emptyMap()))
        }
        if (toolMessages.none { it.content.contains("\"schema\"") }) {
            return toolCall(request, "schema", WorkflowPlanningTools.GET_STEP_SCHEMA, buildJsonObject {
                put("type", "context.set"); put("version", "1")
            })
        }
        val repairing = request.messages.any { it.content.contains("validationErrors") }
        return AgentModelResponse(
            structuredOutput = buildJsonObject {
                put("workflow", buildJsonObject {
                    put("schemaVersion", 1); put("id", "agent-${request.requestId.substringBefore(':')}"); put("version", "1")
                    put("steps", kotlinx.serialization.json.buildJsonArray {
                        add(buildJsonObject {
                            put("id", "set-result"); put("type", "context.set"); put("version", "1"); put("order", 0)
                            put("parameters", buildJsonObject {
                                put("key", "agent.result")
                                if (repairing) put("value", "planned")
                            })
                        })
                    })
                })
                put("explanation", if (repairing) "Corrected deterministic Fake Model proposal." else "Initial Fake Model proposal.")
            },
            tokenUsage = AgentTokenUsage(20, 10)
        )
    }

    private fun toolCall(request: AgentModelRequest, suffix: String, name: String, arguments: JsonObject) =
        AgentModelResponse(
            toolCalls = listOf(AgentRequestedToolCall("${request.requestId}-$suffix", name, arguments)),
            tokenUsage = AgentTokenUsage(10, 3),
            finishReason = AgentFinishReason.TOOL_CALLS
        )
}
