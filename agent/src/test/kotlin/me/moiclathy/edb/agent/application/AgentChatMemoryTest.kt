package me.moiclathy.edb.agent.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentProposalValidation
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import me.moiclathy.edb.agent.persistence.AgentMessageRecord
import me.moiclathy.edb.agent.persistence.AgentRepository
import me.moiclathy.edb.agent.provider.AgentChatRole
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class AgentChatMemoryTest {
    @Test
    fun `recalls only complete newest turns within the character budget`() {
        val repository = mockk<AgentRepository>()
        every { repository.findMemoryMessagesBySession("session", 6) } returns listOf(
            message(1, AgentChatRole.USER, "old-u"), message(2, AgentChatRole.ASSISTANT, "old-a"),
            message(3, AgentChatRole.USER, "new-u"), message(4, AgentChatRole.ASSISTANT, "new-a"),
            message(5, AgentChatRole.USER, "incomplete")
        )
        val memory = AgentChatMemory(repository, AgentChatMemoryPolicy(maxMessages = 6, maxCharacters = 10))

        assertEquals(listOf("new-u", "new-a"), memory.recall("session").map { it.content })
    }

    @Test
    fun `persists only explicit user intent and final proposal as eligible memory`() {
        val repository = mockk<AgentRepository>()
        every { repository.appendMessage(any()) } answers { firstArg<AgentMessageRecord>().copy(id = 1) }
        val memory = AgentChatMemory(repository)
        val context = AgentExecutionContext("session", "run", 7, 11, "correlation")
        val proposal = AgentWorkflowProposal(
            "proposal", "run", "{\"schemaVersion\":1}", "Ready", emptyList(), emptyList(),
            AgentProposalValidation(true), true, NOW
        )

        memory.rememberUser(context, "Build a workflow", NOW)
        memory.rememberAssistant(context, proposal, NOW)

        verify(exactly = 2) { repository.appendMessage(match { it.memoryEligible }) }
        verify { repository.appendMessage(match { it.role == AgentChatRole.USER && it.content == "Build a workflow" }) }
        verify { repository.appendMessage(match { it.role == AgentChatRole.ASSISTANT && it.content.contains("Workflow IR") }) }
    }

    private fun message(id: Long, role: AgentChatRole, content: String) = AgentMessageRecord(
        id, "session", "run-$id", role, content, memoryEligible = true, createdAt = NOW
    )

    companion object { private val NOW = Instant.parse("2026-08-11T15:00:00Z") }
}
