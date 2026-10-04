package me.moiclathy.edb.agent.application

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.agent.persistence.AgentOperationsRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentRetentionManagerTest {
    @Test
    fun `manual cleanup records manager state and disabled scheduler does not start`() = runBlocking {
        val repository = mockk<AgentOperationsRepository>()
        every { repository.deleteExpiredRuns(any(), 10) } returnsMany listOf(2, 0)
        val policy = AgentRetentionPolicy(Duration.ofDays(30), 10, 3, enabled = false)
        val clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val manager = AgentRetentionManager(AgentOperationsService(repository, policy, clock), policy, clock = clock)

        assertFalse(manager.start())
        val result = manager.runNow()

        assertEquals(2, result.deletedRuns)
        assertEquals(result, manager.state.value.lastResult)
        assertFalse(manager.state.value.running)
    }

    @Test
    fun `enabled scheduler starts once and closes gracefully before initial delay`() {
        val repository = mockk<AgentOperationsRepository>()
        val policy = AgentRetentionPolicy(
            Duration.ofDays(30), 10, 3, enabled = true,
            cleanupInterval = Duration.ofHours(1), initialDelay = Duration.ofHours(1)
        )
        val manager = AgentRetentionManager(AgentOperationsService(repository, policy), policy)

        assertTrue(manager.start())
        assertFalse(manager.start())
        assertTrue(manager.state.value.started)
        manager.close()
        assertFalse(manager.state.value.started)
    }

    companion object { private val NOW = Instant.parse("2026-08-11T17:00:00Z") }
}
