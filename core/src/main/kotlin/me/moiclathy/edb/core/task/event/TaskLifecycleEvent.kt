package me.moiclathy.edb.core.task.event

import me.moiclathy.edb.core.event.Event
import me.moiclathy.edb.core.task.TaskStatus
import java.time.Instant

data class TaskLifecycleEvent(
    val taskId: Int,
    val projectId: Int,
    val type: TaskLifecycleEventType,
    val previousStatus: TaskStatus?,
    val status: TaskStatus,
    val attemptCount: Int,
    val details: Map<String, String> = emptyMap(),
    override val occurredAt: Instant
) : Event {
    init {
        require(taskId > 0) { "Task ID must be greater than zero." }
        require(projectId > 0) { "Project ID must be greater than zero." }
        require(attemptCount >= 0) { "Task attempt count must not be negative." }
    }
}
