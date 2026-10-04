package me.moiclathy.edb.agent.application

import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import me.moiclathy.edb.agent.persistence.AgentMessageRecord
import me.moiclathy.edb.agent.persistence.AgentRepository
import me.moiclathy.edb.agent.provider.AgentChatMessage
import me.moiclathy.edb.agent.provider.AgentChatRole
import java.time.Instant
import me.moiclathy.edb.agent.policy.AgentSensitiveDataRedactor

data class AgentChatMemoryPolicy(
    val maxMessages: Int = 12,
    val maxCharacters: Int = 24_000
) {
    init {
        require(maxMessages > 0 && maxMessages % 2 == 0) { "Agent memory message limit must be a positive even number." }
        require(maxCharacters > 0) { "Agent memory character limit must be greater than zero." }
    }
}

class AgentChatMemory(
    private val repository: AgentRepository,
    private val policy: AgentChatMemoryPolicy = AgentChatMemoryPolicy(),
    private val redactor: AgentSensitiveDataRedactor = AgentSensitiveDataRedactor()
) {
    fun recall(sessionId: String): List<AgentChatMessage> {
        val completeTurns = repository.findMemoryMessagesBySession(sessionId, policy.maxMessages).completeTurns()
        var characters = 0
        val selected = ArrayDeque<List<AgentMessageRecord>>()
        for (turn in completeTurns.asReversed()) {
            val size = turn.sumOf { it.content.length }
            if (characters + size > policy.maxCharacters) break
            selected.addFirst(turn)
            characters += size
        }
        return selected.flatten().map { AgentChatMessage(it.role, it.content) }
    }

    fun rememberUser(context: AgentExecutionContext, intent: String, at: Instant) {
        repository.appendMessage(AgentMessageRecord(
            sessionId = context.sessionId, runId = context.runId, role = AgentChatRole.USER,
            content = redactor.redact(intent), memoryEligible = true, createdAt = at
        ))
    }

    fun rememberAssistant(context: AgentExecutionContext, proposal: AgentWorkflowProposal, at: Instant) {
        repository.appendMessage(AgentMessageRecord(
            sessionId = context.sessionId, runId = context.runId, role = AgentChatRole.ASSISTANT,
            content = redactor.redact("${proposal.explanation}\n\nWorkflow IR:\n${proposal.workflowIrJson}"),
            memoryEligible = true, createdAt = at
        ))
    }

    fun messages(sessionId: String): List<AgentMessageRecord> =
        repository.findMemoryMessagesBySession(sessionId, policy.maxMessages)
}

private fun List<AgentMessageRecord>.completeTurns(): List<List<AgentMessageRecord>> {
    val turns = mutableListOf<List<AgentMessageRecord>>()
    var index = 0
    while (index + 1 < size) {
        val user = this[index]
        val assistant = this[index + 1]
        if (user.role == AgentChatRole.USER && assistant.role == AgentChatRole.ASSISTANT) {
            turns += listOf(user, assistant)
            index += 2
        } else {
            index++
        }
    }
    return turns
}
