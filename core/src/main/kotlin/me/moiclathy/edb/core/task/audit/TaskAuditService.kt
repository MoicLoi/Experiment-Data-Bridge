package me.moiclathy.edb.core.task.audit

class TaskAuditService(
    private val repository: TaskAuditRepository
) {
    fun history(taskId: Int): List<TaskAuditEvent> =
        repository.findByTaskId(taskId)
}
