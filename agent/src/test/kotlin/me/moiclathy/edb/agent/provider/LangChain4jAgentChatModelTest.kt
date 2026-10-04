package me.moiclathy.edb.agent.provider

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.output.FinishReason
import dev.langchain4j.model.output.TokenUsage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LangChain4jAgentChatModelTest {
    @Test
    fun `maps tools messages calls and token usage without network`() = runBlocking {
        var captured: ChatRequest? = null
        val delegate = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                captured = request
                handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(listOf(ToolExecutionRequest.builder()
                        .id("call-2").name("validateWorkflowIr").arguments("{\"workflow\":{}}")
                        .build())))
                    .tokenUsage(TokenUsage(12, 4))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build())
            }
        }
        val profile = profile()
        val model = LangChain4jAgentChatModel(delegate, profile)
        val response = model.generate(AgentModelRequest(
            "request-1",
            listOf(
                AgentChatMessage(AgentChatRole.SYSTEM, "Plan safely"),
                AgentChatMessage(AgentChatRole.USER, "Create a workflow"),
                AgentChatMessage(AgentChatRole.ASSISTANT, "Checking", toolCalls = listOf(
                    AgentRequestedToolCall("call-1", "listWorkflowSteps", JsonObject(emptyMap()))
                )),
                AgentChatMessage(AgentChatRole.TOOL, "{\"ok\":true}", "call-1")
            ),
            tools = listOf(AgentToolDefinition("validateWorkflowIr", "Validate IR", buildJsonObject {
                put("type", "object"); put("properties", buildJsonObject {})
            }))
        ))

        assertEquals(1, captured!!.toolSpecifications().size)
        assertIs<ToolExecutionResultMessage>(captured!!.messages().last())
        assertEquals("listWorkflowSteps", (captured!!.messages().last() as ToolExecutionResultMessage).toolName())
        assertEquals("validateWorkflowIr", response.toolCalls.single().name)
        assertEquals(16, response.tokenUsage?.totalTokens)
        assertEquals(AgentFinishReason.TOOL_CALLS, response.finishReason)
    }

    @Test
    fun `parses strict structured JSON response`() = runBlocking {
        val delegate = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                handler.onPartialResponse("{\"workflow\":{},")
                handler.onPartialResponse("\"explanation\":\"ok\"}")
                handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"workflow\":{},\"explanation\":\"ok\"}"))
                    .finishReason(FinishReason.STOP).build())
            }
        }
        val deltas = mutableListOf<String>()
        val response = LangChain4jAgentChatModel(delegate, profile()).generateStreaming(
            AgentModelRequest(
                "request-2", listOf(AgentChatMessage(AgentChatRole.USER, "Plan")),
                responseSchema = buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) }
            ),
            deltas::add
        )
        assertTrue(response.structuredOutput != null)
        assertEquals(AgentFinishReason.STOP, response.finishReason)
        assertEquals(listOf("{\"workflow\":{},", "\"explanation\":\"ok\"}"), deltas)
    }

    @Test
    fun `parses a single markdown fenced JSON response`() = runBlocking {
        val delegate = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("""```json
                        {"workflow":{},"explanation":"ok"}
                        ```""".trimIndent()))
                    .finishReason(FinishReason.STOP).build())
            }
        }

        val response = LangChain4jAgentChatModel(delegate, profile()).generate(
            AgentModelRequest("request-fenced", listOf(AgentChatMessage(AgentChatRole.USER, "Plan")))
        )

        assertTrue(response.structuredOutput != null)
        assertEquals(AgentFinishReason.STOP, response.finishReason)
    }

    @Test
    fun `provider fails closed when referenced environment secret is absent`() {
        val provider = LangChain4jOpenAiModelProvider(LangChain4jOpenAiModelProvider.OPENAI, AgentSecretResolver { null })
        assertFailsWith<AgentModelSecretNotFoundException> { provider.create(profile(apiKeySecretRef = "EDB_TEST_KEY")) }
    }

    private fun profile(apiKeySecretRef: String? = "TEST_KEY") = AgentModelProfile(
        "test", LangChain4jOpenAiModelProvider.OPENAI, "test-model",
        apiKeySecretRef = apiKeySecretRef, timeout = Duration.ofSeconds(5)
    )
}
