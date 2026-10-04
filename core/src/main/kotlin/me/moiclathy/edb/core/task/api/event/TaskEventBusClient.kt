package me.moiclathy.edb.core.task.api.event

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import me.moiclathy.edb.core.event.EventBus
import me.moiclathy.edb.core.event.EventSubscription
import me.moiclathy.edb.core.event.subscribe
import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.application.SubmitPipelineTaskCommand
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TaskEventBusRequestException(
    val operation: TaskApiOperation,
    val correlationId: String,
    code: String,
    message: String
) : EDBException(code, message)

class TaskEventBusTimeoutException(
    val operation: TaskApiOperation,
    val correlationId: String,
    timeout: Duration
) : EDBException(
    code = "TASK_API_TIMEOUT",
    message = "Task EventBus request timed out after ${timeout.toMillis()} ms"
)

class TaskEventBusClientClosedException : EDBException(
    code = "TASK_API_CLIENT_CLOSED",
    message = "Task EventBus client is closed"
)

/** Request/reply facade over the asynchronous task EventBus API. */
class TaskEventBusClient(
    private val eventBus: EventBus,
    private val responseTimeout: Duration = Duration.ofSeconds(5),
    private val clock: Clock = Clock.systemUTC(),
    private val correlationIdFactory: () -> String = { UUID.randomUUID().toString() }
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private val pending = ConcurrentHashMap<String, PendingRequest>()
    private val responseSubscription: EventSubscription

    init {
        require(!responseTimeout.isNegative && !responseTimeout.isZero) {
            "Task EventBus response timeout must be greater than zero."
        }
        responseSubscription = eventBus.subscribe<TaskApiResponseEvent>(::handleResponse)
    }

    suspend fun submitPipelineTask(command: SubmitPipelineTaskCommand): Task = request(
        operation = TaskApiOperation.SUBMIT_PIPELINE
    ) { correlationId, occurredAt ->
        SubmitPipelineTaskRequestEvent(command, correlationId, occurredAt)
    }

    suspend fun getTask(taskId: Int, requestedBy: Int): Task = request(
        operation = TaskApiOperation.GET_TASK
    ) { correlationId, occurredAt ->
        GetTaskRequestEvent(taskId, requestedBy, correlationId, occurredAt)
    }

    suspend fun requestCancellation(taskId: Int, requestedBy: Int): Task = request(
        operation = TaskApiOperation.REQUEST_CANCELLATION
    ) { correlationId, occurredAt ->
        CancelTaskRequestEvent(taskId, requestedBy, correlationId, occurredAt)
    }

    override fun close() {
        val requests = synchronized(lifecycleLock) {
            if (!closed.compareAndSet(false, true)) return
            responseSubscription.cancel()
            val current = pending.values.toList()
            pending.clear()
            current
        }
        requests.forEach {
            it.response.completeExceptionally(TaskEventBusClientClosedException())
        }
    }

    private suspend fun request(
        operation: TaskApiOperation,
        eventFactory: (String, Instant) -> TaskApiRequestEvent
    ): Task {
        val correlationId = correlationIdFactory().also {
            require(it.isNotBlank()) { "Correlation ID must not be blank." }
        }
        val deferred = CompletableDeferred<TaskApiResponseEvent>()
        synchronized(lifecycleLock) {
            if (closed.get()) throw TaskEventBusClientClosedException()
            val previous = pending.putIfAbsent(
                correlationId,
                PendingRequest(operation, deferred)
            )
            require(previous == null) { "Duplicate Task EventBus correlation ID: $correlationId" }
        }

        try {
            eventBus.publish(eventFactory(correlationId, Instant.now(clock)))
            val response = withTimeoutOrNull(responseTimeout.toMillis()) { deferred.await() }
                ?: throw TaskEventBusTimeoutException(operation, correlationId, responseTimeout)
            return response.toTask()
        } finally {
            pending.remove(correlationId)
        }
    }

    private fun handleResponse(response: TaskApiResponseEvent) {
        val request = pending.remove(response.correlationId) ?: return
        if (request.operation != response.operation) {
            request.response.completeExceptionally(
                TaskEventBusRequestException(
                    operation = request.operation,
                    correlationId = response.correlationId,
                    code = "TASK_API_PROTOCOL_ERROR",
                    message = "Task EventBus response operation does not match its request."
                )
            )
            return
        }
        request.response.complete(response)
    }

    private fun TaskApiResponseEvent.toTask(): Task = when (this) {
        is PipelineTaskSubmittedEvent -> task
        is TaskFoundEvent -> task
        is TaskCancellationAcceptedEvent -> task
        is TaskApiFailureEvent -> throw TaskEventBusRequestException(
            operation = operation,
            correlationId = correlationId,
            code = code,
            message = message
        )
    }

    private data class PendingRequest(
        val operation: TaskApiOperation,
        val response: CompletableDeferred<TaskApiResponseEvent>
    )
}
