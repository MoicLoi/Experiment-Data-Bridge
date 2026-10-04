package me.moiclathy.edb.agent.provider

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ControlledAgentChatModelTest {
    @Test
    fun `uses explicitly configured fake fallback after primary failure`() = runBlocking {
        var primaryCalls = 0
        val primary = AgentChatModel { primaryCalls++; error("offline") }
        val fallbackResponse = AgentModelResponse(text = "fallback")
        val controlled = ControlledAgentChatModel(
            primary,
            AgentModelProfile(
                "test", "openai", "model", apiKeySecretRef = "KEY",
                maxConcurrency = 1, requestsPerMinute = 10, circuitBreakerFailures = 1,
                circuitBreakerOpenDuration = Duration.ofMinutes(1)
            ),
            AgentChatModel { fallbackResponse }
        )
        val request = AgentModelRequest("id", listOf(AgentChatMessage(AgentChatRole.USER, "hello")))

        assertEquals(fallbackResponse, controlled.generate(request))
        assertEquals(fallbackResponse, controlled.generate(request))
        assertEquals(1, primaryCalls)
        val snapshot = controlled.snapshot()
        assertEquals(2, snapshot.metrics.requests)
        assertEquals(2, snapshot.metrics.successes)
        assertEquals(2, snapshot.metrics.fallbackUses)
        assertEquals(AgentProviderHealth.CIRCUIT_OPEN, snapshot.health)
    }

    @Test
    fun `does not mix fallback output into a stream after a partial response`() = runBlocking {
        val primary = object : AgentChatModel {
            override suspend fun generate(request: AgentModelRequest) = error("not used")
            override suspend fun generateStreaming(
                request: AgentModelRequest,
                onTextDelta: suspend (String) -> Unit
            ): AgentModelResponse {
                onTextDelta("partial")
                error("connection lost")
            }
        }
        val controlled = ControlledAgentChatModel(
            primary,
            AgentModelProfile(
                "test", "openai", "model", apiKeySecretRef = "KEY",
                maxConcurrency = 1, requestsPerMinute = 10, circuitBreakerFailures = 1,
                circuitBreakerOpenDuration = Duration.ofMinutes(1)
            ),
            AgentChatModel { AgentModelResponse(text = "fallback") }
        )
        val deltas = mutableListOf<String>()
        val request = AgentModelRequest("id", listOf(AgentChatMessage(AgentChatRole.USER, "hello")))

        assertFailsWith<IllegalStateException> {
            controlled.generateStreaming(request, deltas::add)
        }
        assertEquals(listOf("partial"), deltas)
    }
}
