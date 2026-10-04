package me.moiclathy.edb.core.task.audit

interface TaskAuditRepository {
    fun append(event: TaskAuditEvent): TaskAuditEvent

    fun appendAll(events: List<TaskAuditEvent>): List<TaskAuditEvent>

    fun findByTaskId(taskId: Int): List<TaskAuditEvent>
}
