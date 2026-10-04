package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.task.event.TaskLifecycleEvent

@Serializable
data class TaskLifecycleStreamEvent(
    val taskId: Int,
    val projectId: Int,
    val type: String,
    val previousStatus: String?,
    val status: String,
    val attemptCount: Int,
    val details: Map<String, String>,
    val occurredAt: String
) {
    companion object {
        fun from(event: TaskLifecycleEvent) = TaskLifecycleStreamEvent(
            taskId = event.taskId,
            projectId = event.projectId,
            type = event.type.name,
            previousStatus = event.previousStatus?.name,
            status = event.status.name,
            attemptCount = event.attemptCount,
            details = event.details,
            occurredAt = event.occurredAt.toString()
        )
    }
}
