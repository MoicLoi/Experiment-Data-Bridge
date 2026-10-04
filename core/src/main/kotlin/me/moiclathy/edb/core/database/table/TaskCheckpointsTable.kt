package me.moiclathy.edb.core.database.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TaskCheckpointsTable : Table("task_checkpoints") {
    val taskId = reference("task_id", TasksTable.id).uniqueIndex()
    val handlerVersion = varchar("handler_version", 100)
    val cursor = varchar("cursor", 500)
    val artifacts = text("artifacts")
    val state = text("state")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(taskId)
}
