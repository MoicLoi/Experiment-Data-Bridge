package me.moiclathy.edb.agent.application

import me.moiclathy.edb.agent.persistence.AgentTokenQuotaKey
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaLimit
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaRepository
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaScope
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaUsage
import me.moiclathy.edb.agent.provider.AgentModelRequest
import me.moiclathy.edb.agent.provider.AgentModelResponse
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

data class AgentTokenQuotaPolicy(
    val enabled: Boolean = false,
    val dailyUserTokens: Long = 100_000,
    val dailyProjectTokens: Long = 1_000_000
) {
    init { require(dailyUserTokens > 0 && dailyProjectTokens > 0) }
}

data class AgentTokenReservation(
    val date: LocalDate,
    val reservedTokens: Long,
    val keys: List<AgentTokenQuotaKey>
)

data class AgentTokenQuotaSnapshot(
    val enabled: Boolean,
    val date: LocalDate,
    val usages: List<AgentTokenQuotaUsage>
)

class AgentTokenQuotaService(
    private val repository: AgentTokenQuotaRepository,
    val policy: AgentTokenQuotaPolicy,
    private val clock: Clock = Clock.systemUTC()
) {
    fun reserve(request: AgentModelRequest, maxOutputTokens: Int): AgentTokenReservation? {
        if (!policy.enabled) return null
        val userId = requireNotNull(request.actorUserId) { "Token-quota model request requires an actor user id." }
        val projectId = requireNotNull(request.projectId) { "Token-quota model request requires a project id." }
        val estimated = estimateTokens(request, maxOutputTokens)
        val date = LocalDate.now(clock)
        val limits = limits(userId, projectId)
        repository.reserve(date, estimated, limits, Instant.now(clock))
        return AgentTokenReservation(date, estimated, limits.map { it.key })
    }

    fun settle(reservation: AgentTokenReservation?, response: AgentModelResponse) {
        if (reservation == null) return
        val actual = response.tokenUsage?.totalTokens ?: return
        repository.adjust(
            reservation.date,
            Math.subtractExact(actual, reservation.reservedTokens),
            reservation.keys,
            Instant.now(clock)
        )
    }

    fun release(reservation: AgentTokenReservation?) {
        if (reservation == null) return
        repository.adjust(
            reservation.date, -reservation.reservedTokens, reservation.keys, Instant.now(clock)
        )
    }

    fun usage(userId: Int, projectId: Int): List<AgentTokenQuotaUsage> =
        repository.usage(LocalDate.now(clock), limits(userId, projectId))

    fun snapshot(userId: Int, projectId: Int): AgentTokenQuotaSnapshot {
        val date = LocalDate.now(clock)
        return AgentTokenQuotaSnapshot(policy.enabled, date, repository.usage(date, limits(userId, projectId)))
    }

    private fun limits(userId: Int, projectId: Int) = listOf(
        AgentTokenQuotaLimit(AgentTokenQuotaKey(AgentTokenQuotaScope.USER, userId), policy.dailyUserTokens),
        AgentTokenQuotaLimit(AgentTokenQuotaKey(AgentTokenQuotaScope.PROJECT, projectId), policy.dailyProjectTokens)
    )

    private fun estimateTokens(request: AgentModelRequest, maxOutputTokens: Int): Long {
        fun String.utf8Bytes() = toByteArray(Charsets.UTF_8).size.toLong()
        val conservativeInput = request.messages.sumOf { it.content.utf8Bytes() } +
            request.tools.sumOf { it.description.utf8Bytes() + it.inputSchema.toString().utf8Bytes() } +
            (request.responseSchema?.toString()?.utf8Bytes() ?: 0)
        return Math.addExact(conservativeInput, maxOutputTokens.toLong()).coerceAtLeast(1)
    }
}
