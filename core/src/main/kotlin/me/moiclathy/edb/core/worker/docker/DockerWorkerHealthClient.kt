package me.moiclathy.edb.core.worker.docker

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface DockerWorkerHealthClient {
    suspend fun check(endpoint: URI, timeout: Duration): DockerWorkerHealth
}

class HttpDockerWorkerHealthClient(
    private val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .build(),
    private val json: Json = Json
) : DockerWorkerHealthClient {
    override suspend fun check(endpoint: URI, timeout: Duration): DockerWorkerHealth {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Accept", "application/json")
            .GET()
            .build()
        return try {
            val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).awaitHealth()
            if (response.statusCode() !in 200..299) {
                DockerWorkerHealth(false, "Health endpoint returned HTTP ${response.statusCode()}.")
            } else {
                val payload = json.parseToJsonElement(response.body()).jsonObject
                val status = payload["status"]?.jsonPrimitive?.content
                val protocol = payload["protocolVersion"]?.jsonPrimitive?.content?.toIntOrNull()
                if (status == "ok" && protocol == SUPPORTED_PROTOCOL_VERSION) {
                    DockerWorkerHealth(true)
                } else {
                    DockerWorkerHealth(false, "Unsupported worker health response.")
                }
            }
        } catch (failure: Exception) {
            if (failure is java.util.concurrent.CancellationException) throw failure
            DockerWorkerHealth(false, failure.message ?: failure::class.simpleName)
        }
    }

    companion object {
        const val SUPPORTED_PROTOCOL_VERSION = 1
    }
}

private suspend fun <T> CompletableFuture<T>.awaitHealth(): T = suspendCancellableCoroutine { continuation ->
    whenComplete { value, failure ->
        if (failure == null) continuation.resume(value)
        else continuation.resumeWithException(failure.cause ?: failure)
    }
    continuation.invokeOnCancellation { cancel(true) }
}
