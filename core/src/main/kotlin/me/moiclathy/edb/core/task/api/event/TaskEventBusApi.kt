package me.moiclathy.edb.core.task.api.event

import kotlinx.coroutines.CancellationException
import me.moiclathy.edb.core.event.EventBus
import me.moiclathy.edb.core.event.EventSubscription
import me.moiclathy.edb.core.event.subscribe
import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.core.task.application.TaskApplicationService
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Handles type-safe task commands arriving through the shared EventBus. */
class TaskEventBusApi(
    private val eventBus: EventBus,
    private val service: TaskApplicationService,
    private val clock: Clock = Clock.systemUTC()
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private var subscription: EventSubscription? = null

    fun start(): Boolean = synchronized(this) {
        if (!started.compareAndSet(false, true)) return false
        subscription = eventBus.subscribe<TaskApiRequestEvent>(::handle)
        true
    }

    override fun close() = synchronized(this) {
        if (!started.compareAndSet(true, false)) return
        subscription?.cancel()
        subscription = null
    }

    private suspend fun handle(request: TaskApiRequestEvent) {
        val response = try {
            when (request) {
                is SubmitPipelineTaskRequestEvent -> PipelineTaskSubmittedEvent(
                    task = service.submitPipelineTask(request.command),
                    correlationId = request.correlationId,
                    occurredAt = Instant.now(clock)
                )

                is GetTaskRequestEvent -> TaskFoundEvent(
                    task = service.getTask(request.taskId, request.requestedBy),
                    correlationId = request.correlationId,
                    occurredAt = Instant.now(clock)
                )

                is CancelTaskRequestEvent -> TaskCancellationAcceptedEvent(
                    task = service.requestCancellation(request.taskId, request.requestedBy),
                    correlationId = request.correlationId,
                    occurredAt = Instant.now(clock)
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            TaskApiFailureEvent(
                operation = request.operation,
                code = when (failure) {
                    is EDBException -> failure.code
                    is IllegalArgumentException -> "INVALID_REQUEST"
                    else -> "TASK_API_FAILED"
                },
                message = failure.message ?: "Task API request failed.",
                correlationId = request.correlationId,
                occurredAt = Instant.now(clock)
            )
        }
        eventBus.publish(response)
    }
}
