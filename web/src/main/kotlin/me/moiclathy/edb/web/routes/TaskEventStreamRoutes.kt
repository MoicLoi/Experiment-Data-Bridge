package me.moiclathy.edb.web.routes

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.auth.authenticate
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.event.EventBus
import me.moiclathy.edb.core.event.subscribe
import me.moiclathy.edb.core.task.application.TaskApplicationService
import me.moiclathy.edb.core.task.event.TaskLifecycleEvent
import me.moiclathy.edb.web.dto.TaskLifecycleStreamEvent
import me.moiclathy.edb.web.security.requireUserId

fun Route.taskEventStreamRoutes(
    service: TaskApplicationService,
    eventBus: EventBus
) {
    authenticate("auth-jwt") {
        get("/api/v1/projects/{projectId}/tasks/events") {
            val userId = call.requireUserId() ?: return@get
            val projectId = call.parameters["projectId"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Project ID must be a valid integer.")
            service.ensureProjectReadable(projectId, userId)

            call.response.cacheControl(CacheControl.NoCache(null))
            call.response.header(HttpHeaders.Connection, "keep-alive")
            call.respondTextWriter(ContentType.Text.EventStream) {
                val events = Channel<TaskLifecycleEvent>(
                    capacity = EVENT_BUFFER_CAPACITY,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                )
                val subscription = eventBus.subscribe<TaskLifecycleEvent> { event ->
                    if (event.projectId == projectId) events.trySend(event)
                }

                try {
                    write(": connected\n\n")
                    flush()
                    while (currentCoroutineContext().isActive) {
                        val event = withTimeoutOrNull(HEARTBEAT_INTERVAL_MS) {
                            events.receive()
                        }
                        if (event == null) {
                            write(": heartbeat\n\n")
                        } else {
                            write("id: ${event.taskId}-${event.occurredAt.toEpochMilli()}\n")
                            write("event: ${event.type.name.lowercase()}\n")
                            write("data: ${JSON.encodeToString(TaskLifecycleStreamEvent.from(event))}\n\n")
                        }
                        flush()
                    }
                } finally {
                    subscription.cancel()
                    events.close()
                }
            }
        }
    }
}

private const val EVENT_BUFFER_CAPACITY = 64
private const val HEARTBEAT_INTERVAL_MS = 15_000L
private val JSON = Json { encodeDefaults = true }
