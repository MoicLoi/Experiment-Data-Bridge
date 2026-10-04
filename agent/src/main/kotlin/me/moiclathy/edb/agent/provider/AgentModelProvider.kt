package me.moiclathy.edb.agent.provider

interface AgentModelProvider {
    val id: String

    fun create(profile: AgentModelProfile): AgentChatModel
}

class AgentModelProviderRegistry(
    providers: Iterable<AgentModelProvider>
) {
    private val providersById: Map<String, AgentModelProvider>

    init {
        val providerList = providers.toList()
        require(providerList.all { it.id.isNotBlank() && it.id == it.id.trim() }) {
            "Agent model provider ids must not be blank or padded."
        }
        require(providerList.map { it.id }.distinct().size == providerList.size) {
            "Agent model provider ids must be unique."
        }
        providersById = providerList.associateBy { it.id }
    }

    fun registeredProviderIds(): Set<String> = providersById.keys

    fun create(profile: AgentModelProfile): AgentChatModel {
        val provider = providersById[profile.providerId]
            ?: throw AgentModelProviderNotFoundException(profile.providerId)
        return provider.create(profile)
    }
}

class AgentModelProviderNotFoundException(providerId: String) :
    IllegalArgumentException("Agent model provider is not registered: $providerId")
