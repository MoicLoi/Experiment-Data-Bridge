package me.moiclathy.edb.cli.service

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.UUID

data class ProjectConfigurationDownloadResult(
    val path: Path,
    val bytesWritten: Long,
    val contentType: String?
)

interface ProjectConfigurationBinaryTransport {
    fun download(config: TaskServerConfig, path: String, target: Path, force: Boolean): ProjectConfigurationDownloadResult
    fun uploadPackage(config: TaskServerConfig, projectId: Int, file: Path): TaskHttpResponse
}

class JdkProjectConfigurationBinaryTransport(
    private val client: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
) : ProjectConfigurationBinaryTransport {
    override fun download(
        config: TaskServerConfig,
        path: String,
        target: Path,
        force: Boolean
    ): ProjectConfigurationDownloadResult {
        val normalized = target.toAbsolutePath().normalize()
        require(force || !Files.exists(normalized)) { "Export target already exists: $normalized (use --force to replace)" }
        val parent = normalized.parent ?: throw IllegalArgumentException("Export target must have a parent directory.")
        Files.createDirectories(parent)
        val request = request(config, path).GET().build()
        val response = sendStream(config, request)
        response.body().use { body ->
            if (response.statusCode() !in 200..299) {
                throw TaskCliHttpException(response.statusCode(), body.bufferedReader().readText().take(500))
            }
            val temporary = Files.createTempFile(parent, ".edb-config-export-", ".tmp")
            try {
                val bytes = Files.newOutputStream(temporary).use { body.copyTo(it) }
                move(temporary, normalized, force)
                return ProjectConfigurationDownloadResult(
                    normalized,
                    bytes,
                    response.headers().firstValue("Content-Type").orElse(null)
                )
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    override fun uploadPackage(config: TaskServerConfig, projectId: Int, file: Path): TaskHttpResponse {
        require(Files.isRegularFile(file)) { "EDB package does not exist: ${file.toAbsolutePath()}" }
        require(file.fileName.toString().endsWith(".edb", true)) { "EDB package must use the .edb extension." }
        val boundary = "edb-package-${UUID.randomUUID()}"
        val name = file.fileName.toString().replace(Regex("[\r\n\"]"), "_")
        val header = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\nContent-Type: application/octet-stream\r\n\r\n"
            .toByteArray(StandardCharsets.UTF_8)
        val footer = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
        val request = request(config, "/api/v1/projects/$projectId/git/package")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofByteArray(header),
                HttpRequest.BodyPublishers.ofFile(file),
                HttpRequest.BodyPublishers.ofByteArray(footer)
            ))
            .build()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            TaskHttpResponse(response.statusCode(), response.body())
        } catch (failure: Exception) {
            throw TaskCliConnectionException("Cannot import EDB package at ${config.normalizedServerUrl}: ${failure.message}", failure)
        }
    }

    private fun request(config: TaskServerConfig, path: String) = HttpRequest.newBuilder(URI.create(config.normalizedServerUrl + path))
        .timeout(Duration.ofMillis(config.requestTimeoutMs))
        .header("Accept", "application/json, application/octet-stream, application/x-yaml")
        .header("Authorization", "Bearer ${config.token}")

    private fun sendStream(config: TaskServerConfig, request: HttpRequest): HttpResponse<java.io.InputStream> = try {
        client.send(request, HttpResponse.BodyHandlers.ofInputStream())
    } catch (failure: Exception) {
        throw TaskCliConnectionException("Cannot export configuration from ${config.normalizedServerUrl}: ${failure.message}", failure)
    }

    private fun move(source: Path, target: Path, force: Boolean) {
        try {
            val options = mutableListOf(StandardCopyOption.ATOMIC_MOVE)
            if (force) options += StandardCopyOption.REPLACE_EXISTING
            Files.move(source, target, *options.toTypedArray())
        } catch (_: AtomicMoveNotSupportedException) {
            if (force) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING) else Files.move(source, target)
        }
    }
}
