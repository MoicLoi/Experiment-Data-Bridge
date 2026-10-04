package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

class AgentEventStreamClient(
    private val config: TaskServerConfig,
    private val transport: TaskEventStreamTransport = JdkTaskEventStreamTransport()
) {
    fun watch(
        sessionId: String, runId: String? = null, stopOnTerminal: Boolean = runId != null,
        timeoutMs: Long? = null, onEvent: (AgentLifecycleCliEvent) -> Unit
    ): AgentWatchStopReason {
        require(sessionId.isNotBlank()) { "Session ID must not be blank." }
        require(runId == null || runId.isNotBlank()) { "Run ID must not be blank." }
        require(timeoutMs == null || timeoutMs > 0) { "Watch timeout must be greater than zero." }
        val deadline = timeoutMs?.let { System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(it) }
        val parser = Parser()
        try {
            transport.open(config, "/api/v1/agent-sessions/$sessionId/events") { line ->
                if (deadline != null && System.nanoTime() >= deadline) throw Finished(AgentWatchStopReason.TIMEOUT)
                parser.accept(line)?.let { message ->
                    val json = runCatching { Json.parseToJsonElement(message.data).jsonObject }
                        .getOrElse { throw TaskCliConnectionException("Invalid Agent SSE JSON: ${it.message}", it) }
                    val event = AgentLifecycleCliEvent(
                        message.id, json.getValue("sessionId").jsonPrimitive.content,
                        json["runId"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                        json.getValue("projectId").jsonPrimitive.int, json.getValue("type").jsonPrimitive.content,
                        json["runStatus"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
                        json.getValue("detailsJson").jsonPrimitive.content, json.getValue("occurredAt").jsonPrimitive.content
                    )
                    if (runId == null || event.runId == runId) {
                        onEvent(event)
                        if (stopOnTerminal && event.terminal) throw Finished(AgentWatchStopReason.TERMINAL)
                    }
                }
            }
        } catch (finished: Finished) { return finished.reason }
        throw TaskCliConnectionException("Agent SSE stream closed by the server.")
    }

    private data class Message(val id: String?, val data: String)
    private class Parser {
        private var id: String? = null
        private val data = mutableListOf<String>()
        fun accept(line: String): Message? {
            if (line.isEmpty()) {
                if (data.isEmpty()) return null
                return Message(id, data.joinToString("\n")).also { id = null; data.clear() }
            }
            if (line.startsWith(':')) return null
            val separator = line.indexOf(':')
            val field = if (separator < 0) line else line.substring(0, separator)
            val value = if (separator < 0) "" else line.substring(separator + 1).removePrefix(" ")
            when (field) { "id" -> id = value; "data" -> data += value }
            return null
        }
    }
    private class Finished(val reason: AgentWatchStopReason) : RuntimeException(null, null, false, false)
}
