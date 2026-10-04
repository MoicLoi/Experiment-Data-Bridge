package me.moiclathy.edb.core.datafile.retention

import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.database.table.DataFilesTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class MySqlDataFileRetentionRepository : DataFileRetentionRepository {
    override fun countExpiredDeleted(cutoff: Instant): Long = transaction {
        DataFilesTable.selectAll()
            .where {
                (DataFilesTable.status eq DataFileStatus.DELETED) and
                    (DataFilesTable.updatedAt less cutoff)
            }
            .count()
    }

    override fun deleteExpiredDeleted(cutoff: Instant, limit: Int): Int {
        require(limit > 0) { "DataFile retention batch size must be positive." }
        return transaction {
            val ids = DataFilesTable.select(DataFilesTable.id)
                .where {
                    (DataFilesTable.status eq DataFileStatus.DELETED) and
                        (DataFilesTable.updatedAt less cutoff)
                }
                .orderBy(
                    DataFilesTable.updatedAt to SortOrder.ASC,
                    DataFilesTable.id to SortOrder.ASC
                )
                .limit(limit)
                .map { it[DataFilesTable.id] }
            if (ids.isEmpty()) return@transaction 0
            DataFilesTable.deleteWhere { DataFilesTable.id inList ids }
        }
    }
}
