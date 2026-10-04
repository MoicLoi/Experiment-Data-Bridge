package me.moiclathy.edb.core.task.audit

import me.moiclathy.edb.core.task.TaskStatus
import java.time.Instant

data class TaskAuditEvent(
    val id: Long? = null,
    val taskId: Int,
    val type: TaskAuditEventType,
    val fromStatus: TaskStatus?,
    val toStatus: TaskStatus?,
    val actorUserId: Int?,
    val details: Map<String, String> = emptyMap(),
    val createdAt: Instant
) {
    init {
        require(id == null || id > 0) { "Task audit event ID must be greater than zero." }
        require(taskId > 0) { "Task ID must be greater than zero." }
        require(actorUserId == null || actorUserId > 0) {
            "Task audit actor ID must be greater than zero."
        }
    }
}
