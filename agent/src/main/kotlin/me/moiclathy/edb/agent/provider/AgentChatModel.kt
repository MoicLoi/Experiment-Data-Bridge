package me.moiclathy.edb.agent.provider

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.moiclathy.edb.agent.model.AgentTokenUsage

fun interface AgentChatModel {
    suspend fun generate(request: AgentModelRequest): AgentModelResponse

    suspend fun generateStreaming(
        request: AgentModelRequest,
        onTextDelta: suspend (String) -> Unit
    ): AgentModelResponse = generate(request).also { response ->
        response.text?.let { onTextDelta(it) }
    }
}

data class AgentModelRequest(
    val requestId: String,
    val messages: List<AgentChatMessage>,
    val tools: List<AgentToolDefinition> = emptyList(),
    val responseSchema: JsonObject? = null,
    val modelProfileId: String? = null,
    val actorUserId: Int? = null,
    val projectId: Int? = null
) {
    init {
        require(requestId.isNotBlank() && requestId == requestId.trim()) {
            "Agent model request id must not be blank or contain surrounding whitespace."
        }
        require(messages.isNotEmpty()) { "Agent model request must contain at least one message." }
        require(modelProfileId == null || (modelProfileId.isNotBlank() && modelProfileId == modelProfileId.trim())) {
            "Agent model profile id must not be blank or padded."
        }
        require(actorUserId == null || actorUserId > 0) { "Agent model actor user id must be greater than zero." }
        require(projectId == null || projectId > 0) { "Agent model project id must be greater than zero." }
        require(tools.map { it.name }.distinct().size == tools.size) {
            "Agent model tool names must be unique within a request."
        }
    }
}

data class AgentChatMessage(
    val role: AgentChatRole,
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<AgentRequestedToolCall> = emptyList()
) {
    init {
        require(content.isNotBlank()) { "Agent chat message content must not be blank." }
        require((role == AgentChatRole.TOOL) == (toolCallId != null)) {
            "Only tool-result messages require a tool call id."
        }
        require(toolCallId == null || toolCallId.isNotBlank()) { "Tool call id must not be blank." }
        require(role == AgentChatRole.ASSISTANT || toolCalls.isEmpty()) {
            "Only assistant messages can contain tool calls."
        }
    }
}

enum class AgentChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

data class AgentToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
) {
    init {
        require(name.isNotBlank() && name == name.trim()) { "Agent tool name must not be blank or padded." }
        require(description.isNotBlank()) { "Agent tool description must not be blank." }
    }
}

data class AgentModelResponse(
    val text: String? = null,
    val structuredOutput: JsonElement? = null,
    val toolCalls: List<AgentRequestedToolCall> = emptyList(),
    val tokenUsage: AgentTokenUsage? = null,
    val finishReason: AgentFinishReason = AgentFinishReason.STOP
) {
    init {
        require(text != null || structuredOutput != null || toolCalls.isNotEmpty()) {
            "Agent model response must contain text, structured output, or at least one tool call."
        }
        require(text == null || text.isNotBlank()) { "Agent model response text must not be blank." }
        require(toolCalls.map { it.id }.distinct().size == toolCalls.size) {
            "Agent model tool call ids must be unique within a response."
        }
    }
}

data class AgentRequestedToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject
) {
    init {
        require(id.isNotBlank() && id == id.trim()) { "Agent tool call id must not be blank or padded." }
        require(name.isNotBlank() && name == name.trim()) { "Agent tool call name must not be blank or padded." }
    }
}

enum class AgentFinishReason {
    STOP,
    TOOL_CALLS,
    LENGTH,
    CONTENT_FILTER,
    ERROR,
    UNKNOWN
}
