package me.moiclathy.edb.core.task.api.event

import me.moiclathy.edb.core.event.Event
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.application.SubmitPipelineTaskCommand
import java.time.Instant
import java.util.UUID

enum class TaskApiOperation {
    SUBMIT_PIPELINE,
    GET_TASK,
    REQUEST_CANCELLATION
}

sealed interface TaskApiRequestEvent : Event {
    val correlationId: String
    val operation: TaskApiOperation
}

data class SubmitPipelineTaskRequestEvent(
    val command: SubmitPipelineTaskCommand,
    override val correlationId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : TaskApiRequestEvent {
    override val operation = TaskApiOperation.SUBMIT_PIPELINE
}

data class GetTaskRequestEvent(
    val taskId: Int,
    val requestedBy: Int,
    override val correlationId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : TaskApiRequestEvent {
    override val operation = TaskApiOperation.GET_TASK

    init {
        require(taskId > 0) { "Task ID must be greater than zero." }
        require(requestedBy > 0) { "Requesting user ID must be greater than zero." }
    }
}

data class CancelTaskRequestEvent(
    val taskId: Int,
    val requestedBy: Int,
    override val correlationId: String = UUID.randomUUID().toString(),
    override val occurredAt: Instant = Instant.now()
) : TaskApiRequestEvent {
    override val operation = TaskApiOperation.REQUEST_CANCELLATION

    init {
        require(taskId > 0) { "Task ID must be greater than zero." }
        require(requestedBy > 0) { "Requesting user ID must be greater than zero." }
    }
}

sealed interface TaskApiResponseEvent : Event {
    val correlationId: String
    val operation: TaskApiOperation
}

data class PipelineTaskSubmittedEvent(
    val task: Task,
    override val correlationId: String,
    override val occurredAt: Instant
) : TaskApiResponseEvent {
    override val operation = TaskApiOperation.SUBMIT_PIPELINE
}

data class TaskFoundEvent(
    val task: Task,
    override val correlationId: String,
    override val occurredAt: Instant
) : TaskApiResponseEvent {
    override val operation = TaskApiOperation.GET_TASK
}

data class TaskCancellationAcceptedEvent(
    val task: Task,
    override val correlationId: String,
    override val occurredAt: Instant
) : TaskApiResponseEvent {
    override val operation = TaskApiOperation.REQUEST_CANCELLATION
}

data class TaskApiFailureEvent(
    override val operation: TaskApiOperation,
    val code: String,
    val message: String,
    override val correlationId: String,
    override val occurredAt: Instant
) : TaskApiResponseEvent
