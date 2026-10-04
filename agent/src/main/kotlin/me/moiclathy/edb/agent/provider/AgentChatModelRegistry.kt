package me.moiclathy.edb.agent.provider

import java.util.concurrent.ConcurrentHashMap
import me.moiclathy.edb.agent.application.AgentTokenQuotaService

class AgentChatModelRegistry(
    profiles: Iterable<AgentModelProfile>,
    private val providers: AgentModelProviderRegistry,
    builtInModels: Map<String, AgentChatModel> = emptyMap(),
    private val fallback: AgentChatModel? = null
) {
    private val profilesById: Map<String, AgentModelProfile>
    private val builtIns: Map<String, AgentChatModel>
    private val models = ConcurrentHashMap<String, AgentChatModel>()

    init {
        val configured = profiles.toList()
        require(configured.map { it.id }.distinct().size == configured.size) {
            "Agent model profile ids must be unique."
        }
        require(builtInModels.keys.none { it.isBlank() || it != it.trim() }) {
            "Built-in Agent model profile ids must not be blank or padded."
        }
        require((configured.map { it.id }.toSet() intersect builtInModels.keys).isEmpty()) {
            "Configured and built-in Agent model profile ids must not overlap."
        }
        profilesById = configured.associateBy { it.id }
        builtIns = builtInModels.toMap()
    }

    fun availableProfileIds(): Set<String> = builtIns.keys + profilesById.keys

    fun maxOutputTokens(profileId: String): Int = profilesById[profileId]?.maxOutputTokens
        ?: if (profileId in builtIns) 4_096 else throw AgentModelProfileNotFoundException(profileId)

    fun catalog(): List<AgentModelProfileCatalogEntry> = (
        builtIns.keys.map { id ->
            AgentModelProfileCatalogEntry(id, "built-in", id, true, true, builtIn = true)
        } + profilesById.values.map { profile ->
            AgentModelProfileCatalogEntry(
                profile.id, profile.providerId, profile.modelName,
                profile.toolCallingEnabled, profile.structuredOutputEnabled, builtIn = false
            )
        }
    ).sortedBy { it.id }

    fun providerStatuses(): List<AgentProviderProfileStatus> = catalog().map { entry ->
        val profile = profilesById[entry.id]
        val model = models[entry.id]
        val snapshot = (model as? ControlledAgentChatModel)?.snapshot()
        AgentProviderProfileStatus(
            id = entry.id,
            providerId = entry.providerId,
            modelName = entry.modelName,
            builtIn = entry.builtIn,
            initialized = entry.builtIn || model != null,
            apiKeyConfigured = entry.builtIn || profile?.apiKeySecretRef != null,
            customEndpointConfigured = profile?.baseUrl != null,
            health = snapshot?.health ?: if (entry.builtIn) AgentProviderHealth.HEALTHY else AgentProviderHealth.UNINITIALIZED,
            consecutiveFailures = snapshot?.consecutiveFailures ?: 0,
            circuitOpenUntil = snapshot?.circuitOpenUntil,
            metrics = snapshot?.metrics ?: AgentModelCallMetrics(0, 0, 0, 0, 0, 0, 0, 0, null)
        )
    }

    fun resolve(profileId: String): AgentChatModel {
        builtIns[profileId]?.let { return it }
        val profile = profilesById[profileId] ?: throw AgentModelProfileNotFoundException(profileId)
        return models.computeIfAbsent(profileId) {
            ControlledAgentChatModel(
                primary = providers.create(profile),
                profile = profile,
                fallback = fallback
            )
        }
    }
}

data class AgentModelProfileCatalogEntry(
    val id: String,
    val providerId: String,
    val modelName: String,
    val toolCallingEnabled: Boolean,
    val structuredOutputEnabled: Boolean,
    val builtIn: Boolean
)

data class AgentProviderProfileStatus(
    val id: String,
    val providerId: String,
    val modelName: String,
    val builtIn: Boolean,
    val initialized: Boolean,
    val apiKeyConfigured: Boolean,
    val customEndpointConfigured: Boolean,
    val health: AgentProviderHealth,
    val consecutiveFailures: Int,
    val circuitOpenUntil: java.time.Instant?,
    val metrics: AgentModelCallMetrics
)

class AgentModelProfileCatalogService(
    private val registry: AgentChatModelRegistry,
    val defaultProfileId: String
) {
    init {
        require(defaultProfileId in registry.availableProfileIds()) {
            "Default Agent model profile is not registered: $defaultProfileId"
        }
    }

    fun list(): List<AgentModelProfileCatalogEntry> = registry.catalog()
    fun providerStatuses(): List<AgentProviderProfileStatus> = registry.providerStatuses()
}

class RoutingAgentChatModel(
    private val registry: AgentChatModelRegistry,
    private val quota: AgentTokenQuotaService? = null
) : AgentChatModel {
    override suspend fun generate(request: AgentModelRequest): AgentModelResponse = execute(request) { model ->
        model.generate(request)
    }

    override suspend fun generateStreaming(
        request: AgentModelRequest,
        onTextDelta: suspend (String) -> Unit
    ): AgentModelResponse = execute(request) { model -> model.generateStreaming(request, onTextDelta) }

    private suspend fun execute(
        request: AgentModelRequest,
        invocation: suspend (AgentChatModel) -> AgentModelResponse
    ): AgentModelResponse {
        val profileId = request.requiredProfileId()
        val reservation = quota?.reserve(request, registry.maxOutputTokens(profileId))
        return try {
            invocation(registry.resolve(profileId)).also { quota?.settle(reservation, it) }
        } catch (failure: Throwable) {
            quota?.release(reservation)
            throw failure
        }
    }
}

class AgentModelProfileNotFoundException(profileId: String) :
    IllegalArgumentException("Agent model profile is not registered: $profileId")

private fun AgentModelRequest.requiredProfileId(): String = modelProfileId
    ?: throw IllegalArgumentException("Agent model request requires a model profile id for dynamic routing: $requestId")
