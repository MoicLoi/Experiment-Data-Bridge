package me.moiclathy.edb.agent.provider

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.model.AgentTokenUsage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AgentModelProviderRegistryTest {
    @Test
    fun `provider registry creates a provider-neutral scripted model`() = runBlocking {
        val expected = AgentModelResponse(
            structuredOutput = buildJsonObject { put("workflowId", "planned-flow") },
            tokenUsage = AgentTokenUsage(20, 8)
        )
        val provider = ScriptedProvider(ArrayDeque(listOf(expected)))
        val registry = AgentModelProviderRegistry(listOf(provider))
        val profile = AgentModelProfile("test-profile", provider.id, "scripted-model")
        val model = registry.create(profile)
        val request = AgentModelRequest(
            requestId = "request-1",
            messages = listOf(AgentChatMessage(AgentChatRole.USER, "Create a workflow"))
        )

        assertEquals(expected, model.generate(request))
        assertEquals(profile, provider.createdWith)
        assertEquals(request, provider.received.single())
    }

    @Test
    fun `registry rejects duplicate and unknown providers`() {
        assertFailsWith<IllegalArgumentException> {
            AgentModelProviderRegistry(listOf(ScriptedProvider(), ScriptedProvider()))
        }
        val registry = AgentModelProviderRegistry(emptyList())
        assertFailsWith<AgentModelProviderNotFoundException> {
            registry.create(AgentModelProfile("profile", "missing", "model"))
        }
    }

    @Test
    fun `chat model registry routes each request by profile and creates models lazily`() = runBlocking {
        val first = AgentModelResponse(text = "first")
        val second = AgentModelResponse(text = "second")
        val provider = ScriptedProvider(ArrayDeque(listOf(first, second)))
        val profiles = listOf(
            AgentModelProfile("profile-a", provider.id, "model-a"),
            AgentModelProfile("profile-b", provider.id, "model-b")
        )
        val registry = AgentChatModelRegistry(profiles, AgentModelProviderRegistry(listOf(provider)))
        val router = RoutingAgentChatModel(registry)

        assertEquals(first, router.generate(request("request-a", "profile-a")))
        assertEquals(second, router.generate(request("request-b", "profile-b")))
        assertEquals(listOf("profile-a", "profile-b"), provider.createdProfiles.map { it.id })
        assertEquals(setOf("profile-a", "profile-b"), registry.availableProfileIds())
    }

    @Test
    fun `routing rejects a missing or unknown profile`() = runBlocking {
        val registry = AgentChatModelRegistry(emptyList(), AgentModelProviderRegistry(emptyList()))
        val router = RoutingAgentChatModel(registry)

        assertFailsWith<IllegalArgumentException> {
            router.generate(AgentModelRequest("missing", listOf(AgentChatMessage(AgentChatRole.USER, "Plan"))))
        }
        assertFailsWith<AgentModelProfileNotFoundException> {
            router.generate(request("unknown", "does-not-exist"))
        }
    }

    private fun request(id: String, profileId: String) = AgentModelRequest(
        requestId = id,
        messages = listOf(AgentChatMessage(AgentChatRole.USER, "Create a workflow")),
        modelProfileId = profileId
    )

    private class ScriptedProvider(
        private val responses: ArrayDeque<AgentModelResponse> = ArrayDeque()
    ) : AgentModelProvider {
        override val id = "scripted"
        var createdWith: AgentModelProfile? = null
        val createdProfiles = mutableListOf<AgentModelProfile>()
        val received = mutableListOf<AgentModelRequest>()

        override fun create(profile: AgentModelProfile): AgentChatModel {
            createdWith = profile
            createdProfiles += profile
            return AgentChatModel { request ->
                received += request
                check(responses.isNotEmpty()) { "No scripted Agent model response remains." }
                responses.removeFirst()
            }
        }
    }
}
