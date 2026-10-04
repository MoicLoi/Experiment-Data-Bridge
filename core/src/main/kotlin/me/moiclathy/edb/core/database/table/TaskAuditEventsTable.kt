package me.moiclathy.edb.core.database.table

import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.audit.TaskAuditEventType
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object TaskAuditEventsTable : LongIdTable("task_audit_events") {
    val taskId = reference("task_id", TasksTable.id)
    val eventType = enumerationByName("event_type", 30, TaskAuditEventType::class)
    val fromStatus = enumerationByName("from_status", 20, TaskStatus::class).nullable()
    val toStatus = enumerationByName("to_status", 20, TaskStatus::class).nullable()
    val actorUserId = reference("actor_user_id", UsersTable.id).nullable()
    val details = text("details").nullable()
    val createdAt = timestamp("created_at")
}
