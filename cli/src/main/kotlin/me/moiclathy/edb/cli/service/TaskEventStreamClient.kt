package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

fun interface TaskEventStreamTransport {
    fun open(config: TaskServerConfig, path: String, onLine: (String) -> Unit)
}

class JdkTaskEventStreamTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) : TaskEventStreamTransport {
    override fun open(config: TaskServerConfig, path: String, onLine: (String) -> Unit) {
        val request = HttpRequest.newBuilder(URI.create(config.normalizedServerUrl + path))
            .timeout(Duration.ofMillis(config.requestTimeoutMs))
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer ${config.token}")
            .GET()
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TaskCliConnectionException("SSE connection was interrupted.", failure)
        } catch (failure: Exception) {
            throw TaskCliConnectionException("Cannot open SSE stream at ${config.normalizedServerUrl}: ${failure.message}", failure)
        }
        response.body().use { body ->
            if (response.statusCode() !in 200..299) {
                val message = body.bufferedReader().readText().take(500).ifBlank { "No response body" }
                throw TaskCliHttpException(response.statusCode(), message)
            }
            try {
                BufferedReader(InputStreamReader(body)).useLines { lines -> lines.forEach(onLine) }
            } catch (failure: IOException) {
                throw TaskCliConnectionException("SSE stream failed: ${failure.message}", failure)
            }
        }
    }
}

class TaskEventStreamClient(
    private val config: TaskServerConfig,
    private val transport: TaskEventStreamTransport = JdkTaskEventStreamTransport()
) {
    fun watch(
        projectId: Int,
        taskId: Int? = null,
        stopOnTerminal: Boolean = taskId != null,
        timeoutMs: Long? = null,
        onEvent: (TaskLifecycleCliEvent) -> Unit
    ): TaskWatchStopReason {
        require(projectId > 0) { "Project ID must be greater than zero." }
        require(taskId == null || taskId > 0) { "Task ID must be greater than zero." }
        require(timeoutMs == null || timeoutMs > 0) { "Watch timeout must be greater than zero." }
        val deadline = timeoutMs?.let { System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(it) }
        val parser = SseParser()
        try {
            transport.open(config, "/api/v1/projects/$projectId/tasks/events") { line ->
                if (deadline != null && System.nanoTime() >= deadline) {
                    throw WatchFinished(TaskWatchStopReason.TIMEOUT)
                }
                parser.accept(line)?.let { message ->
                    val event = decode(message)
                    if (taskId == null || event.taskId == taskId) {
                        onEvent(event)
                        if (stopOnTerminal && event.isTerminal) {
                            throw WatchFinished(TaskWatchStopReason.TERMINAL)
                        }
                    }
                }
            }
        } catch (finished: WatchFinished) {
            return finished.reason
        }
        throw TaskCliConnectionException("SSE stream closed by the server.")
    }

    private fun decode(message: SseMessage): TaskLifecycleCliEvent {
        val json = runCatching { Json.parseToJsonElement(message.data).jsonObject }
            .getOrElse { throw TaskCliConnectionException("Invalid SSE event JSON: ${it.message}", it) }
        return TaskLifecycleCliEvent(
            eventId = message.id,
            taskId = json.int("taskId"),
            projectId = json.int("projectId"),
            type = json.string("type"),
            previousStatus = json.nullableString("previousStatus"),
            status = json.string("status"),
            attemptCount = json.int("attemptCount"),
            details = json.stringMap("details"),
            occurredAt = json.string("occurredAt")
        )
    }

    private data class SseMessage(val id: String?, val event: String?, val data: String)

    private class SseParser {
        private var id: String? = null
        private var event: String? = null
        private val data = mutableListOf<String>()

        fun accept(line: String): SseMessage? {
            if (line.isEmpty()) {
                if (data.isEmpty()) return null
                return SseMessage(id, event, data.joinToString("\n")).also {
                    id = null
                    event = null
                    data.clear()
                }
            }
            if (line.startsWith(':')) return null
            val separator = line.indexOf(':')
            val field = if (separator < 0) line else line.substring(0, separator)
            val value = if (separator < 0) "" else line.substring(separator + 1).removePrefix(" ")
            when (field) {
                "id" -> id = value
                "event" -> event = value
                "data" -> data += value
            }
            return null
        }
    }

    private class WatchFinished(val reason: TaskWatchStopReason) : RuntimeException(null, null, false, false)

    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int
    private fun JsonObject.nullableString(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.stringMap(name: String) = getValue(name).jsonObject.mapValues { it.value.jsonPrimitive.content }
}

enum class TaskWatchStopReason {
    TERMINAL,
    TIMEOUT,
    ALREADY_TERMINAL
}
