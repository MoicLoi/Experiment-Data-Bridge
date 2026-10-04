package me.moiclathy.edb.core.audit.repository


import me.moiclathy.edb.core.audit.model.AuditLogPage
import me.moiclathy.edb.core.audit.model.AuditLogQuery
import me.moiclathy.edb.core.database.model.AuditLog
import me.moiclathy.edb.core.database.table.AuditLogsTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class MySqlAuditLogRepository : AuditLogRepository {

    override fun search(
        query: AuditLogQuery
    ): AuditLogPage {

        return transaction {

            val condition = buildCondition(query)

            val total =
                AuditLogsTable
                    .selectAll()
                    .where {
                        condition
                    }
                    .count()

            val rows =
                AuditLogsTable
                    .selectAll()
                    .where {
                        condition
                    }
                    .orderBy(
                        AuditLogsTable.createdAt,
                        SortOrder.DESC
                    )
                    .limit(query.pageSize)
                    .offset(query.offset)
                    .map(::toModel)

            AuditLogPage(
                items = rows,
                page = query.page,
                pageSize = query.pageSize,
                total = total
            )
        }
    }

    override fun findById(
        id: Long
    ): AuditLog? {

        require(id > 0) {
            "Audit log ID must be greater than zero."
        }

        return transaction {

            AuditLogsTable
                .selectAll()
                .where {
                    AuditLogsTable.id eq id
                }
                .limit(1)
                .map(::toModel)
                .singleOrNull()
        }
    }

    override fun count(
        query: AuditLogQuery
    ): Long {

        return transaction {

            val condition = buildCondition(query)

            AuditLogsTable
                .selectAll()
                .where {
                    condition
                }
                .count()
        }
    }

    private fun buildCondition(
        query: AuditLogQuery
    ): Op<Boolean> {

        var condition: Op<Boolean> = Op.TRUE

        query.userId?.let {
            condition =
                condition and
                        (AuditLogsTable.userId eq it)
        }

        query.action
            ?.takeIf { it.isNotBlank() }
            ?.let {
                condition =
                    condition and
                            (AuditLogsTable.action eq it)
            }

        query.resourceType
            ?.takeIf { it.isNotBlank() }
            ?.let {
                condition =
                    condition and
                            (AuditLogsTable.resourceType eq it)
            }

        query.resourceId?.let {
            condition =
                condition and
                        (AuditLogsTable.resourceId eq it)
        }

        query.from?.let {
            condition =
                condition and
                        (AuditLogsTable.createdAt greaterEq it)
        }

        query.to?.let {
            condition =
                condition and
                        (AuditLogsTable.createdAt lessEq it)
        }

        return condition
    }

    private fun toModel(
        row: org.jetbrains.exposed.sql.ResultRow
    ): AuditLog {

        return AuditLog(
            id =
                row[AuditLogsTable.id],

            userId =
                row[AuditLogsTable.userId],

            action =
                row[AuditLogsTable.action],

            resourceType =
                row[AuditLogsTable.resourceType]?: "",

            resourceId =
                row[AuditLogsTable.resourceId],

            details =
                row[AuditLogsTable.details],

            createdAt =
                row[AuditLogsTable.createdAt]
        )
    }
}