package me.moiclathy.edb.core.task.retention

import me.moiclathy.edb.core.database.table.TasksTable
import me.moiclathy.edb.core.task.TaskStatus
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class MySqlTaskRetentionRepository : TaskRetentionRepository {
    override fun countExpired(cutoff: Instant): Long = transaction {
        TasksTable
            .selectAll()
            .where {
                (TasksTable.status inList TERMINAL_STATUSES) and
                    (TasksTable.completedAt less cutoff)
            }
            .count()
    }

    override fun deleteExpired(cutoff: Instant, limit: Int): Int {
        require(limit > 0) { "Cleanup limit must be greater than zero." }
        return transaction {
            val ids = TasksTable
                .select(TasksTable.id)
                .where {
                    (TasksTable.status inList TERMINAL_STATUSES) and
                        (TasksTable.completedAt less cutoff)
                }
                .orderBy(
                    TasksTable.completedAt to SortOrder.ASC,
                    TasksTable.id to SortOrder.ASC
                )
                .limit(limit)
                .map { it[TasksTable.id] }
            if (ids.isEmpty()) return@transaction 0
            TasksTable.deleteWhere { TasksTable.id inList ids }
        }
    }

    private companion object {
        val TERMINAL_STATUSES = listOf(
            TaskStatus.COMPLETED,
            TaskStatus.FAILED,
            TaskStatus.TIMED_OUT,
            TaskStatus.CANCELLED
        )
    }
}
