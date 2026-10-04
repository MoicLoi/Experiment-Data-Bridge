package me.moiclathy.edb.agent.support

import me.moiclathy.edb.agent.provider.AgentChatModel
import me.moiclathy.edb.agent.provider.AgentModelRequest
import me.moiclathy.edb.agent.provider.AgentModelResponse

class ScriptedAgentChatModel(
    responses: Iterable<AgentModelResponse>
) : AgentChatModel {
    private val remaining = ArrayDeque(responses.toList())
    val requests = mutableListOf<AgentModelRequest>()

    override suspend fun generate(request: AgentModelRequest): AgentModelResponse {
        requests += request
        check(remaining.isNotEmpty()) { "No scripted Agent model response remains." }
        return remaining.removeFirst()
    }
}
