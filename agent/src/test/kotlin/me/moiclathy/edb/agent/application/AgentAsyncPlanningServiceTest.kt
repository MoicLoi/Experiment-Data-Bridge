package me.moiclathy.edb.agent.application

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.agent.model.*
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.policy.AgentPolicy
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class AgentAsyncPlanningServiceTest {
    @Test
    fun `returns started run before planning completes and cooperatively cancels it`() = runBlocking {
        val planning = mockk<AgentPlanningApplicationService>()
        val definition = AgentDefinition("planner", "1", "Planner", "Plan", "fake", emptySet(), "policy")
        val policy = AgentPolicy("policy", setOf(AgentCapability.DISCOVER_STEPS))
        val session = AgentSession("session", USER_ID, PROJECT_ID, "planner", "1", AgentSessionStatus.ACTIVE, NOW, NOW)
        val running = AgentRun("run", "session", "correlation", AgentRunStatus.PLANNING, "fake", NOW, NOW)
        val cancelled = running.transitionTo(AgentRunStatus.CANCELLED, NOW)
        every { planning.getSession("session", USER_ID) } returns session
        coEvery { planning.plan("session", USER_ID, definition, policy, "intent", 2, any()) } coAnswers {
            arg<suspend (AgentRun) -> Unit>(6).invoke(running)
            awaitCancellation()
        }
        coEvery { planning.cancelRun("run", USER_ID) } returns cancelled
        val service = AgentAsyncPlanningService(planning)
        try {
            assertEquals(running, service.start("session", USER_ID, definition, policy, "intent"))
            assertEquals(cancelled, service.cancel("run", USER_ID))
            coVerify { planning.cancelRun("run", USER_ID) }
        } finally {
            service.close()
        }
    }

    companion object {
        private const val USER_ID = 7
        private const val PROJECT_ID = 11
        private val NOW = Instant.parse("2026-08-11T22:00:00Z")
    }
}
