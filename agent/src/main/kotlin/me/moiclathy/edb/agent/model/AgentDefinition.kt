package me.moiclathy.edb.agent.model

data class AgentDefinition(
    val id: String,
    val version: String,
    val description: String,
    val systemPrompt: String,
    val modelProfileId: String,
    val enabledTools: Set<String> = emptySet(),
    val policyId: String
) {
    init {
        requireIdentifier(id, "Agent id")
        requireIdentifier(version, "Agent version")
        require(description.isNotBlank()) { "Agent description must not be blank." }
        require(systemPrompt.isNotBlank()) { "Agent system prompt must not be blank." }
        requireIdentifier(modelProfileId, "Model profile id")
        requireIdentifier(policyId, "Agent policy id")
        require(enabledTools.none { it.isBlank() || it != it.trim() }) {
            "Enabled Agent tool names must not be blank or contain surrounding whitespace."
        }
    }
}

internal fun requireIdentifier(value: String, label: String) {
    require(value.isNotBlank()) { "$label must not be blank." }
    require(value == value.trim()) { "$label must not contain surrounding whitespace." }
}
