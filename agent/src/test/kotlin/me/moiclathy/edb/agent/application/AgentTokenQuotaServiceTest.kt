package me.moiclathy.edb.agent.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.moiclathy.edb.agent.model.AgentTokenUsage
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaKey
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaRepository
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaScope
import me.moiclathy.edb.agent.provider.AgentChatMessage
import me.moiclathy.edb.agent.provider.AgentChatRole
import me.moiclathy.edb.agent.provider.AgentModelRequest
import me.moiclathy.edb.agent.provider.AgentModelResponse
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class AgentTokenQuotaServiceTest {
    @Test
    fun `reserves both scopes and settles conservative estimate to actual usage`() {
        val repository = mockk<AgentTokenQuotaRepository>()
        every { repository.reserve(any(), any(), any(), any()) } returns emptyList()
        every { repository.adjust(any(), any(), any(), any()) } returns Unit
        val service = AgentTokenQuotaService(
            repository, AgentTokenQuotaPolicy(true, 1_000, 10_000), Clock.fixed(NOW, ZoneOffset.UTC)
        )
        val request = AgentModelRequest(
            "request", listOf(AgentChatMessage(AgentChatRole.USER, "hello")),
            modelProfileId = "profile", actorUserId = 7, projectId = 11
        )

        val reservation = requireNotNull(service.reserve(request, 100))
        service.settle(reservation, AgentModelResponse(text = "ok", tokenUsage = AgentTokenUsage(5, 3)))

        assertEquals(105, reservation.reservedTokens)
        assertEquals(
            listOf(AgentTokenQuotaKey(AgentTokenQuotaScope.USER, 7), AgentTokenQuotaKey(AgentTokenQuotaScope.PROJECT, 11)),
            reservation.keys
        )
        verify { repository.adjust(reservation.date, -97, reservation.keys, NOW) }
    }

    @Test
    fun `failed model call releases the full reservation`() {
        val repository = mockk<AgentTokenQuotaRepository>()
        every { repository.reserve(any(), any(), any(), any()) } returns emptyList()
        every { repository.adjust(any(), any(), any(), any()) } returns Unit
        val service = AgentTokenQuotaService(
            repository, AgentTokenQuotaPolicy(true, 1_000, 10_000), Clock.fixed(NOW, ZoneOffset.UTC)
        )
        val reservation = requireNotNull(service.reserve(AgentModelRequest(
            "request", listOf(AgentChatMessage(AgentChatRole.USER, "hello")),
            modelProfileId = "profile", actorUserId = 7, projectId = 11
        ), 100))

        service.release(reservation)

        verify { repository.adjust(reservation.date, -105, reservation.keys, NOW) }
    }

    companion object { private val NOW = Instant.parse("2026-08-11T18:00:00Z") }
}
