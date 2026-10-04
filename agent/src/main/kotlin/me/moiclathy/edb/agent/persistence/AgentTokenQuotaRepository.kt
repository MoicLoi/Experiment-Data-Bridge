package me.moiclathy.edb.agent.persistence

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import me.moiclathy.edb.core.exception.EDBException

enum class AgentTokenQuotaScope { USER, PROJECT }
data class AgentTokenQuotaKey(val scope: AgentTokenQuotaScope, val id: Int)
data class AgentTokenQuotaLimit(val key: AgentTokenQuotaKey, val limit: Long)
data class AgentTokenQuotaUsage(val key: AgentTokenQuotaKey, val date: LocalDate, val usedTokens: Long, val limit: Long)

interface AgentTokenQuotaRepository {
    fun reserve(date: LocalDate, amount: Long, limits: List<AgentTokenQuotaLimit>, at: Instant): List<AgentTokenQuotaUsage>
    fun adjust(date: LocalDate, amountDelta: Long, keys: List<AgentTokenQuotaKey>, at: Instant)
    fun usage(date: LocalDate, limits: List<AgentTokenQuotaLimit>): List<AgentTokenQuotaUsage>
}

class MySqlAgentTokenQuotaRepository : AgentTokenQuotaRepository {
    private val lock = Any()

    override fun reserve(
        date: LocalDate,
        amount: Long,
        limits: List<AgentTokenQuotaLimit>,
        at: Instant
    ): List<AgentTokenQuotaUsage> = synchronized(lock) {
        require(amount > 0)
        transaction {
            val current = limits.map { limit -> usageRow(date, limit) }
            current.firstOrNull { Math.addExact(it.usedTokens, amount) > it.limit }?.let {
                throw AgentTokenQuotaExceededException(it.key, it.usedTokens, it.limit, amount)
            }
            current.forEach { row -> writeUsage(row.key, date, row.usedTokens + amount, at) }
            current.map { it.copy(usedTokens = it.usedTokens + amount) }
        }
    }

    override fun adjust(date: LocalDate, amountDelta: Long, keys: List<AgentTokenQuotaKey>, at: Instant) = synchronized(lock) {
        transaction {
            keys.forEach { key ->
                val row = find(key, date) ?: return@forEach
                writeUsage(key, date, (row.usedTokens + amountDelta).coerceAtLeast(0), at)
            }
        }
    }

    override fun usage(date: LocalDate, limits: List<AgentTokenQuotaLimit>): List<AgentTokenQuotaUsage> = synchronized(lock) {
        transaction { limits.map { usageRow(date, it) } }
    }

    private fun usageRow(date: LocalDate, limit: AgentTokenQuotaLimit): AgentTokenQuotaUsage {
        val used = find(limit.key, date)?.usedTokens ?: 0
        return AgentTokenQuotaUsage(limit.key, date, used, limit.limit)
    }

    private fun find(key: AgentTokenQuotaKey, date: LocalDate): AgentTokenQuotaUsage? =
        AgentTokenQuotaUsageTable.selectAll().where {
            (AgentTokenQuotaUsageTable.scopeType eq key.scope.name) and
                (AgentTokenQuotaUsageTable.scopeId eq key.id) and
                (AgentTokenQuotaUsageTable.usageDate eq date)
        }.limit(1).singleOrNull()?.let {
            AgentTokenQuotaUsage(key, date, it[AgentTokenQuotaUsageTable.usedTokens], Long.MAX_VALUE)
        }

    private fun writeUsage(key: AgentTokenQuotaKey, date: LocalDate, tokens: Long, at: Instant) {
        val updated = AgentTokenQuotaUsageTable.update({
            (AgentTokenQuotaUsageTable.scopeType eq key.scope.name) and
                (AgentTokenQuotaUsageTable.scopeId eq key.id) and
                (AgentTokenQuotaUsageTable.usageDate eq date)
        }) { row -> row[usedTokens] = tokens; row[updatedAt] = at }
        if (updated == 0) AgentTokenQuotaUsageTable.insert { row ->
            row[scopeType] = key.scope.name; row[scopeId] = key.id; row[usageDate] = date
            row[usedTokens] = tokens; row[updatedAt] = at
        }
    }
}

class AgentTokenQuotaExceededException(
    val key: AgentTokenQuotaKey,
    val usedTokens: Long,
    val limit: Long,
    val requestedTokens: Long
) : EDBException(
    "AGENT_TOKEN_QUOTA_EXCEEDED",
    "Daily Agent Token quota exceeded for ${key.scope.name.lowercase()} ${key.id}."
)
