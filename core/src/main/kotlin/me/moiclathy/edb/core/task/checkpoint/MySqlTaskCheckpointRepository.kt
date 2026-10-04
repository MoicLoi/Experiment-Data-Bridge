package me.moiclathy.edb.core.task.checkpoint

import me.moiclathy.edb.core.database.table.TaskCheckpointsTable
import me.moiclathy.edb.core.task.persistence.TaskPayloadCodec
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class MySqlTaskCheckpointRepository : TaskCheckpointRepository {
    override fun findByTaskId(taskId: Int): TaskCheckpoint? {
        require(taskId > 0)
        return transaction {
            TaskCheckpointsTable.selectAll()
                .where { TaskCheckpointsTable.taskId eq taskId }
                .limit(1)
                .singleOrNull()
                ?.let { row ->
                    TaskCheckpoint(
                        taskId = row[TaskCheckpointsTable.taskId],
                        handlerVersion = row[TaskCheckpointsTable.handlerVersion],
                        cursor = row[TaskCheckpointsTable.cursor],
                        artifacts = TaskPayloadCodec.decode(row[TaskCheckpointsTable.artifacts]),
                        state = TaskPayloadCodec.decode(row[TaskCheckpointsTable.state]),
                        updatedAt = row[TaskCheckpointsTable.updatedAt]
                    )
                }
        }
    }

    override fun save(checkpoint: TaskCheckpoint): TaskCheckpoint = transaction {
        val updated = TaskCheckpointsTable.update({ TaskCheckpointsTable.taskId eq checkpoint.taskId }) { row ->
            row[handlerVersion] = checkpoint.handlerVersion
            row[cursor] = checkpoint.cursor
            row[artifacts] = TaskPayloadCodec.encode(checkpoint.artifacts)
            row[state] = TaskPayloadCodec.encode(checkpoint.state)
            row[updatedAt] = checkpoint.updatedAt
        }
        if (updated == 0) {
            TaskCheckpointsTable.insert { row ->
                row[taskId] = checkpoint.taskId
                row[handlerVersion] = checkpoint.handlerVersion
                row[cursor] = checkpoint.cursor
                row[artifacts] = TaskPayloadCodec.encode(checkpoint.artifacts)
                row[state] = TaskPayloadCodec.encode(checkpoint.state)
                row[updatedAt] = checkpoint.updatedAt
            }
        }
        checkpoint
    }

    override fun deleteByTaskId(taskId: Int) {
        require(taskId > 0)
        transaction { TaskCheckpointsTable.deleteWhere { TaskCheckpointsTable.taskId eq taskId } }
    }
}
