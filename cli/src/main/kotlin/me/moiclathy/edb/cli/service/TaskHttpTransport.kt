package me.moiclathy.edb.cli.service

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class TaskServerConfig(
    val serverUrl: String,
    val token: String,
    val requestTimeoutMs: Long = 30_000
) {
    val normalizedServerUrl: String

    init {
        require(token.isNotBlank()) { "Bearer token is required. Use --token or EDB_TOKEN." }
        require(requestTimeoutMs > 0) { "Request timeout must be greater than zero." }
        val uri = runCatching { URI.create(serverUrl.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid server URL: $serverUrl") }
        require(uri.scheme == "http" || uri.scheme == "https") { "Server URL must use http or https." }
        require(!uri.host.isNullOrBlank()) { "Server URL must include a host." }
        normalizedServerUrl = serverUrl.trim().trimEnd('/')
    }
}

data class TaskHttpRequest(val method: String, val path: String, val body: String? = null)
data class TaskHttpResponse(val status: Int, val body: String)

fun interface TaskHttpTransport {
    fun execute(config: TaskServerConfig, request: TaskHttpRequest): TaskHttpResponse
}

class JdkTaskHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) : TaskHttpTransport {
    override fun execute(config: TaskServerConfig, request: TaskHttpRequest): TaskHttpResponse {
        val builder = HttpRequest.newBuilder(URI.create(config.normalizedServerUrl + request.path))
            .timeout(Duration.ofMillis(config.requestTimeoutMs))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${config.token}")
        if (request.body == null) {
            builder.method(request.method, HttpRequest.BodyPublishers.noBody())
        } else {
            builder.header("Content-Type", "application/json")
            builder.method(request.method, HttpRequest.BodyPublishers.ofString(request.body))
        }
        return try {
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            TaskHttpResponse(response.statusCode(), response.body())
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TaskCliConnectionException("Request was interrupted.", failure)
        } catch (failure: Exception) {
            throw TaskCliConnectionException("Cannot reach ${config.normalizedServerUrl}: ${failure.message}", failure)
        }
    }
}

class TaskCliConnectionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class TaskCliHttpException(val status: Int, message: String) : RuntimeException("Server returned HTTP $status: $message")
