package me.moiclathy.edb.agent.provider

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.chat.request.ResponseFormatType
import dev.langchain4j.model.chat.request.json.JsonSchema
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.model.output.FinishReason
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.model.AgentTokenUsage

fun interface AgentSecretResolver {
    fun resolve(reference: String): String?
}

class EnvironmentAgentSecretResolver(
    private val environment: (String) -> String? = System::getenv
) : AgentSecretResolver {
    override fun resolve(reference: String): String? = environment(reference)?.takeIf(String::isNotBlank)
}

class LangChain4jOpenAiModelProvider(
    override val id: String,
    private val secrets: AgentSecretResolver
) : AgentModelProvider {
    init { require(id in setOf(OPENAI, OPENAI_COMPATIBLE)) }

    override fun create(profile: AgentModelProfile): AgentChatModel {
        require(profile.providerId == id) { "Profile provider ${profile.providerId} does not match $id." }
        val secretRef = requireNotNull(profile.apiKeySecretRef) { "Profile ${profile.id} requires an API key secret reference." }
        val apiKey = secrets.resolve(secretRef) ?: throw AgentModelSecretNotFoundException(secretRef)
        if (id == OPENAI_COMPATIBLE) requireNotNull(profile.baseUrl) {
            "OpenAI-compatible profile ${profile.id} requires a base URL."
        }
        val builder = OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(profile.modelName)
            .temperature(profile.temperature)
            .maxCompletionTokens(profile.maxOutputTokens)
            .timeout(profile.timeout)
            .strictTools(false)
            .parallelToolCalls(false)
        profile.baseUrl?.let { builder.baseUrl(it.toString().trimEnd('/')) }
        return LangChain4jAgentChatModel(builder.build(), profile)
    }

    companion object {
        const val OPENAI = "openai"
        const val OPENAI_COMPATIBLE = "openai-compatible"
    }
}

class LangChain4jAgentChatModel(
    private val delegate: StreamingChatModel,
    private val profile: AgentModelProfile,
    private val json: Json = Json
) : AgentChatModel {
    override suspend fun generate(request: AgentModelRequest): AgentModelResponse =
        generateStreaming(request) {}

    override suspend fun generateStreaming(
        request: AgentModelRequest,
        onTextDelta: suspend (String) -> Unit
    ): AgentModelResponse = withTimeout(profile.timeout.toMillis()) {
        coroutineScope {
            val deltas = Channel<String>(Channel.UNLIMITED)
            val completed = CompletableDeferred<ChatResponse>()
            val consumer = launch { for (delta in deltas) onTextDelta(delta) }
            try {
                withContext(Dispatchers.IO) {
                    delegate.chat(request.toChatRequest(profile), object : StreamingChatResponseHandler {
                        override fun onPartialResponse(partialResponse: String) {
                            if (partialResponse.isNotEmpty()) deltas.trySend(partialResponse)
                        }

                        override fun onCompleteResponse(completeResponse: ChatResponse) {
                            completed.complete(completeResponse)
                        }

                        override fun onError(error: Throwable) {
                            completed.completeExceptionally(error)
                        }
                    })
                }
                completed.await().toAgentResponse(json)
            } finally {
                deltas.close()
                consumer.join()
            }
        }
    }
}

private fun AgentModelRequest.toChatRequest(profile: AgentModelProfile): ChatRequest = ChatRequest.builder()
    .messages(messages.toLangChainMessages())
    .toolSpecifications(if (profile.toolCallingEnabled) tools.map(AgentToolDefinition::toSpecification) else emptyList())
    .apply {
        if (profile.structuredOutputEnabled && responseSchema != null) {
            responseFormat(responseSchema.toResponseFormat())
        }
    }
    .build()

private fun List<AgentChatMessage>.toLangChainMessages(): List<ChatMessage> {
    val toolNames = mutableMapOf<String, String>()
    return map { message ->
        when (message.role) {
            AgentChatRole.SYSTEM -> SystemMessage.from(message.content)
            AgentChatRole.USER -> UserMessage.from(message.content)
            AgentChatRole.ASSISTANT -> {
                val calls = message.toolCalls.map { call ->
                    toolNames[call.id] = call.name
                    ToolExecutionRequest.builder().id(call.id).name(call.name).arguments(call.arguments.toString()).build()
                }
                if (calls.isEmpty()) AiMessage.from(message.content) else AiMessage.from(message.content, calls)
            }
            AgentChatRole.TOOL -> ToolExecutionResultMessage.from(
                requireNotNull(message.toolCallId),
                toolNames[message.toolCallId] ?: "unknown_tool",
                message.content
            )
        }
    }
}

private fun AgentToolDefinition.toSpecification(): ToolSpecification = ToolSpecification.fromJson(
    buildJsonObject {
        put("name", name); put("description", description); put("parameters", inputSchema)
    }.toString()
)

private fun JsonObject.toResponseFormat(): ResponseFormat {
    val root = ToolSpecification.fromJson(buildJsonObject {
        put("name", "workflow_proposal"); put("description", "Strict Workflow proposal response")
        put("parameters", this@toResponseFormat)
    }.toString()).parameters()
    return ResponseFormat.builder().type(ResponseFormatType.JSON).jsonSchema(
        JsonSchema.builder().name("workflow_proposal").rootElement(root).build()
    ).build()
}

private fun dev.langchain4j.model.chat.response.ChatResponse.toAgentResponse(json: Json): AgentModelResponse {
    val message = aiMessage()
    val calls = message.toolExecutionRequests().map { call ->
        AgentRequestedToolCall(call.id(), call.name(), json.parseToJsonElement(call.arguments()) as JsonObject)
    }
    val text = message.text()?.takeIf(String::isNotBlank)
    val structured = text?.takeIf { calls.isEmpty() }?.let { json.parseStructuredModelOutput(it) }
    val usage = tokenUsage()?.let { AgentTokenUsage(
        (it.inputTokenCount() ?: 0).toLong(), (it.outputTokenCount() ?: 0).toLong()
    ) }
    return AgentModelResponse(
        text = text,
        structuredOutput = structured,
        toolCalls = calls,
        tokenUsage = usage,
        finishReason = when (finishReason()) {
            FinishReason.STOP -> AgentFinishReason.STOP
            FinishReason.LENGTH -> AgentFinishReason.LENGTH
            FinishReason.TOOL_EXECUTION -> AgentFinishReason.TOOL_CALLS
            FinishReason.CONTENT_FILTER -> AgentFinishReason.CONTENT_FILTER
            else -> AgentFinishReason.UNKNOWN
        }
    )
}

private fun Json.parseStructuredModelOutput(text: String) =
    runCatching { parseToJsonElement(text) }.getOrNull()
        ?: JSON_CODE_FENCE.matchEntire(text.trim())
            ?.groupValues
            ?.get(1)
            ?.let { fenced -> runCatching { parseToJsonElement(fenced.trim()) }.getOrNull() }

private val JSON_CODE_FENCE = Regex("""(?s)```(?:json)?\s*(.*?)\s*```""", RegexOption.IGNORE_CASE)

class AgentModelSecretNotFoundException(reference: String) :
    IllegalStateException("Agent model API key environment variable is missing: $reference")
