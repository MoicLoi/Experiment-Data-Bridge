package me.moiclathy.edb.agent.model

import me.moiclathy.edb.agent.policy.AgentPolicy

class AgentDefinitionRegistry(definitions: Iterable<AgentDefinition>) {
    private val entries = definitions.associateBy { it.id to it.version }.also {
        require(it.size == definitions.count()) { "Agent definition ids and versions must be unique." }
    }

    fun require(id: String, version: String): AgentDefinition = entries[id to version]
        ?: throw AgentDefinitionNotFoundException(id, version)
}

class AgentPolicyRegistry(policies: Iterable<AgentPolicy>) {
    private val entries = policies.associateBy(AgentPolicy::id).also {
        require(it.size == policies.count()) { "Agent policy ids must be unique." }
    }

    fun require(id: String): AgentPolicy = entries[id] ?: throw AgentPolicyNotFoundException(id)
}

class AgentDefinitionNotFoundException(id: String, version: String) :
    IllegalArgumentException("Agent definition was not found: $id@$version")
class AgentPolicyNotFoundException(id: String) : IllegalArgumentException("Agent policy was not found: $id")
