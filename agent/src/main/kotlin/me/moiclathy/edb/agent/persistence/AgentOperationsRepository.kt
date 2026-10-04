package me.moiclathy.edb.agent.persistence

import me.moiclathy.edb.agent.model.AgentRunStatus
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant

data class AgentRunMetrics(
    val totalRuns: Long,
    val received: Long,
    val planning: Long,
    val validating: Long,
    val awaitingApproval: Long,
    val executing: Long,
    val waitingTask: Long,
    val completed: Long,
    val failed: Long,
    val cancelled: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val averageExecutionMillis: Double?
)

interface AgentOperationsRepository {
    fun metrics(): AgentRunMetrics
    fun countExpiredRuns(cutoff: Instant): Long
    fun deleteExpiredRuns(cutoff: Instant, limit: Int): Int
}

class MySqlAgentOperationsRepository : AgentOperationsRepository {
    override fun metrics(): AgentRunMetrics = transaction {
        val runs = AgentRunsTable.selectAll().toList()
        fun count(status: AgentRunStatus) = runs.count { it[AgentRunsTable.status] == status }.toLong()
        val durations = runs.mapNotNull { row ->
            row[AgentRunsTable.completedAt]?.let { Duration.between(row[AgentRunsTable.startedAt], it).toMillis() }
        }
        AgentRunMetrics(
            totalRuns = runs.size.toLong(), received = count(AgentRunStatus.RECEIVED),
            planning = count(AgentRunStatus.PLANNING), validating = count(AgentRunStatus.VALIDATING),
            awaitingApproval = count(AgentRunStatus.AWAITING_APPROVAL), executing = count(AgentRunStatus.EXECUTING),
            waitingTask = count(AgentRunStatus.WAITING_TASK),
            completed = count(AgentRunStatus.COMPLETED), failed = count(AgentRunStatus.FAILED),
            cancelled = count(AgentRunStatus.CANCELLED),
            inputTokens = runs.sumOf { it[AgentRunsTable.inputTokens] ?: 0L },
            outputTokens = runs.sumOf { it[AgentRunsTable.outputTokens] ?: 0L },
            averageExecutionMillis = durations.takeIf { it.isNotEmpty() }?.average()
        )
    }

    override fun countExpiredRuns(cutoff: Instant): Long = transaction {
        AgentRunsTable.selectAll().where {
            (AgentRunsTable.status inList TERMINAL_STATUSES) and (AgentRunsTable.completedAt less cutoff)
        }.count()
    }

    override fun deleteExpiredRuns(cutoff: Instant, limit: Int): Int = transaction {
        require(limit > 0) { "Agent retention cleanup limit must be greater than zero." }
        val runIds = AgentRunsTable.select(AgentRunsTable.id).where {
            (AgentRunsTable.status inList TERMINAL_STATUSES) and (AgentRunsTable.completedAt less cutoff)
        }.orderBy(AgentRunsTable.completedAt to SortOrder.ASC, AgentRunsTable.id to SortOrder.ASC)
            .limit(limit).map { it[AgentRunsTable.id] }
        if (runIds.isEmpty()) return@transaction 0
        val proposalIds = AgentWorkflowProposalsTable.select(AgentWorkflowProposalsTable.id)
            .where { AgentWorkflowProposalsTable.runId inList runIds }
            .map { it[AgentWorkflowProposalsTable.id] }
        if (proposalIds.isNotEmpty()) AgentProposalDecisionsTable.deleteWhere { proposalId inList proposalIds }
        AgentWorkflowProposalsTable.deleteWhere { runId inList runIds }
        AgentToolCallsTable.deleteWhere { runId inList runIds }
        AgentMessagesTable.deleteWhere { runId inList runIds }
        AgentAuditEventsTable.deleteWhere { runId inList runIds }
        AgentRunsTable.deleteWhere { id inList runIds }
    }

    private companion object {
        val TERMINAL_STATUSES = listOf(AgentRunStatus.COMPLETED, AgentRunStatus.FAILED, AgentRunStatus.CANCELLED)
    }
}
