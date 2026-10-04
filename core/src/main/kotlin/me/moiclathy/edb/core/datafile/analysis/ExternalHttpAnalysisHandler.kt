package me.moiclathy.edb.core.datafile.analysis

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.persistence.DataFileMetadataJsonCodec
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import me.moiclathy.edb.core.task.error.TaskExecutionException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExternalHttpAnalysisHandlerConfig(
    val id: String,
    val analysisType: String,
    val tool: String,
    val endpoint: URI,
    val outputFormat: DataFileFormat = DataFileFormat.JSON,
    val bearerToken: String? = null,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val requestTimeout: Duration = Duration.ofMinutes(5),
    val errorCodePrefix: String = tool.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')
) {
    init {
        require(id.isNotBlank() && analysisType.isNotBlank() && tool.isNotBlank()) {
            "External handler id, analysis type and tool must not be blank."
        }
        require(endpoint.scheme in setOf("http", "https") && endpoint.host != null) { "External worker endpoint must be HTTP(S)." }
        require(outputFormat != DataFileFormat.UNKNOWN) { "External worker output format must be known." }
        require(bearerToken == null || bearerToken.isNotBlank()) { "External worker bearer token must not be blank." }
        require(!connectTimeout.isNegative && !connectTimeout.isZero) { "External worker connect timeout must be positive." }
        require(!requestTimeout.isNegative && !requestTimeout.isZero) { "External worker request timeout must be positive." }
        require(errorCodePrefix.matches(Regex("[A-Z0-9_]+"))) { "External worker error code prefix is invalid." }
    }
}

open class ExternalHttpAnalysisHandler(
    private val config: ExternalHttpAnalysisHandlerConfig,
    private val json: Json = Json,
    private val metadataCodec: DataFileMetadataJsonCodec = DataFileMetadataJsonCodec(json),
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(config.connectTimeout)
        // Uvicorn does not support h2c upgrade; forcing HTTP/1.1 also keeps streamed multipart
        // bodies intact when the JDK client would otherwise probe an upgrade first.
        .version(HttpClient.Version.HTTP_1_1)
        .build()
) : AnalysisHandler {
    override val id: String = config.id
    override val analysisType: String = config.analysisType
    override val tool: String = config.tool
    override val outputFormat: DataFileFormat = config.outputFormat

    override suspend fun analyze(input: AnalysisHandlerInput): AnalysisHandlerOutput {
        require(input.sources.isNotEmpty()) { "External analysis requires at least one source." }
        val boundary = "edb-${UUID.randomUUID()}"
        val request = HttpRequest.newBuilder(config.endpoint)
            .timeout(config.requestTimeout)
            .header("Accept", "application/octet-stream")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .apply { config.bearerToken?.let { header("Authorization", "Bearer $it") } }
            .POST(multipartBody(input, boundary))
            .build()
        val response = try {
            client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).await()
        } catch (failure: Exception) {
            if (failure is java.util.concurrent.CancellationException) throw failure
            throw IOException("External analysis worker request failed: ${failure.message}", failure)
        }
        if (response.statusCode() !in 200..299) {
            val detail = response.body().use { String(it.readNBytes(MAX_ERROR_BYTES), StandardCharsets.UTF_8) }
            throw responseFailure(response.statusCode(), detail)
        }
        return try {
            val fileName = response.headers().firstValue(FILE_NAME_HEADER).orElse(null)
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("External worker response is missing $FILE_NAME_HEADER.")
            val mediaType = response.headers().firstValue("Content-Type").orElse(null)
                ?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }
            val metadata = response.headers().firstValue(METADATA_HEADER).orElse(null)
                ?.let { encoded ->
                    val decoded = String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
                    metadataCodec.decode(decoded)
                } ?: DataFileMetadata.EMPTY
            val outputFormat = response.headers().firstValue(FORMAT_HEADER).orElse(null)
                ?.let { value ->
                    runCatching { DataFileFormat.valueOf(value.uppercase()) }
                        .getOrElse { throw IOException("External worker returned an invalid $FORMAT_HEADER: $value") }
                }
            AnalysisHandlerOutput(fileName, response.body(), mediaType, metadata, outputFormat)
        } catch (failure: Exception) {
            response.body().close()
            throw IOException("External worker returned an invalid success response: ${failure.message}", failure)
        }
    }

    private fun multipartBody(input: AnalysisHandlerInput, boundary: String): HttpRequest.BodyPublisher {
        val publishers = mutableListOf<HttpRequest.BodyPublisher>()
        fun bytes(value: String) = HttpRequest.BodyPublishers.ofByteArray(value.toByteArray(StandardCharsets.UTF_8))
        publishers += bytes("--$boundary\r\nContent-Disposition: form-data; name=\"manifest\"\r\nContent-Type: application/json\r\n\r\n")
        publishers += bytes(manifest(input))
        publishers += bytes("\r\n")
        input.sources.forEachIndexed { index, source ->
            val partName = "source-$index"
            val fileName = source.dataFile.originalFileName.replace(Regex("[\"\r\n]"), "_")
            val mediaType = source.dataFile.mediaType ?: "application/octet-stream"
            publishers += bytes(
                "--$boundary\r\nContent-Disposition: form-data; name=\"$partName\"; filename=\"$fileName\"\r\n" +
                    "Content-Type: $mediaType\r\n\r\n"
            )
            publishers += HttpRequest.BodyPublishers.ofInputStream { source.content }
            publishers += bytes("\r\n")
        }
        publishers += bytes("--$boundary--\r\n")
        return HttpRequest.BodyPublishers.concat(*publishers.toTypedArray())
    }

    private fun manifest(input: AnalysisHandlerInput): String = json.encodeToString(
        JsonElement.serializer(),
        buildJsonObject {
            put("schemaVersion", 1)
            put("handlerId", id)
            put("analysisType", analysisType)
            put("parameters", buildJsonObject {
                input.parameters.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
            })
            put("sources", buildJsonArray {
                input.sources.forEachIndexed { index, source ->
                    add(buildJsonObject {
                        put("partName", "source-$index")
                        source.dataFile.id?.let { put("dataFileId", it) }
                        put("fileName", source.dataFile.originalFileName)
                        put("format", source.dataFile.format.name)
                        source.dataFile.mediaType?.let { put("mediaType", it) }
                        source.dataFile.sizeBytes?.let { put("sizeBytes", it) }
                        source.dataFile.sha256?.let { put("sha256", it) }
                    })
                }
            })
        }
    )

    private fun responseFailure(status: Int, detail: String): TaskExecutionException {
        val retryable = status == 429 || status >= 500
        val category = when {
            status == 401 || status == 403 -> TaskErrorCategory.AUTHORIZATION
            status == 404 -> TaskErrorCategory.CONFIGURATION
            status in 400..499 && status != 429 -> TaskErrorCategory.VALIDATION
            else -> TaskErrorCategory.TRANSIENT
        }
        return TaskExecutionException(
            code = "${config.errorCodePrefix}_WORKER_HTTP_$status",
            category = category,
            message = "External analysis worker '${config.tool}' returned HTTP $status${detail.takeIf { it.isNotBlank() }?.let { ": ${it.take(500)}" }.orEmpty()}",
            retryable = retryable,
            details = mapOf("status" to status.toString(), "endpoint" to config.endpoint.toString(), "tool" to config.tool)
        )
    }

    companion object {
        const val FILE_NAME_HEADER = "X-EDB-File-Name"
        const val METADATA_HEADER = "X-EDB-Metadata-Base64"
        const val FORMAT_HEADER = "X-EDB-Format"
        private const val MAX_ERROR_BYTES = 8_192
    }
}

private suspend fun <T> CompletableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    whenComplete { value, failure ->
        if (failure == null) continuation.resume(value)
        else continuation.resumeWithException(failure.cause ?: failure)
    }
    continuation.invokeOnCancellation { cancel(true) }
}
