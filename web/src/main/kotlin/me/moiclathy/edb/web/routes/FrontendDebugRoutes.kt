package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.web.plugin.RequestIdKey
import org.slf4j.LoggerFactory

@Serializable
data class FrontendDebugConfigResponse(val enabled: Boolean)

@Serializable
data class FrontendDebugEventRequest(
    val level: String = "INFO",
    val category: String,
    val message: String,
    val path: String? = null,
    val details: Map<String, String> = emptyMap(),
    val occurredAt: String? = null
)

fun Route.frontendDebugRoutes(config: AppConfig) {
    val enabled = config.logging.debugMode
    val logger = LoggerFactory.getLogger("FrontendDebug")

    get("/api/v1/debug/config") {
        call.respond(FrontendDebugConfigResponse(enabled))
    }

    post("/api/v1/debug/events") {
        if (!enabled) {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }
        val event = call.receive<FrontendDebugEventRequest>()
        val category = safe(event.category, 64)
        val message = redact(safe(event.message, 1_000))
        val path = redact(safe(event.path ?: "-", 300))
        val details = event.details.entries.take(20).joinToString(",") { (key, value) ->
            "${safe(key, 64)}=${redact(safe(value, 300))}"
        }
        val line = "requestId=${call.attributes.getOrNull(RequestIdKey)} category=$category path=$path message=$message details={$details} clientAt=${safe(event.occurredAt ?: "-", 64)}"
        when (event.level.uppercase()) {
            "ERROR" -> logger.error(line)
            "WARN" -> logger.warn(line)
            else -> logger.info(line)
        }
        call.respond(HttpStatusCode.Accepted)
    }
}

private fun safe(value: String, max: Int): String = value.replace(Regex("[\\r\\n\\t]+"), " ").take(max)

private fun redact(value: String): String = value
    .replace(Regex("(?i)(password|passwd|token|authorization|cookie|secret|api[-_]?key)\\s*[:=]\\s*[^ ,;}]+"), "$1=[REDACTED]")
    .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~+/-]+=*"), "Bearer [REDACTED]")
