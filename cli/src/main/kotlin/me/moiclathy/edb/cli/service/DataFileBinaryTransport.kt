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
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import java.io.FilterInputStream
import java.io.InputStream

interface DataFileBinaryTransport {
    fun upload(config: TaskServerConfig, projectId: Int, file: Path, mediaType: String?): TaskHttpResponse
    fun download(config: TaskServerConfig, projectId: Int, dataFileId: Int, target: Path, force: Boolean): DataFileDownloadResult
}

private fun InputStream.limited(limit:Long):InputStream=object:FilterInputStream(this){var remaining=limit;override fun read():Int{if(remaining<=0)return -1;val value=super.read();if(value>=0)remaining--;return value};override fun read(buffer:ByteArray,offset:Int,length:Int):Int{if(remaining<=0)return -1;val read=super.read(buffer,offset,minOf(length.toLong(),remaining).toInt());if(read>0)remaining-=read;return read}}

class JdkDataFileBinaryTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
    private val chunkSize:Long=4L*1024*1024,
    private val progress:(Long,Long)->Unit={uploaded,total->System.err.print("\r[EDB] Upload ${if(total==0L)100 else uploaded*100/total}%")}
) : DataFileBinaryTransport {
    override fun upload(
        config: TaskServerConfig,
        projectId: Int,
        file: Path,
        mediaType: String?
    ): TaskHttpResponse {
        require(Files.isRegularFile(file)) { "Upload file does not exist: ${file.toAbsolutePath()}" }
        val fileName = file.fileName.toString().replace(Regex("[\r\n\"]"), "_")
        val total=Files.size(file);val state=file.resolveSibling(".${file.fileName}.edb-upload")
        var uploadId=if(Files.isRegularFile(state))Files.readString(state).trim() else ""
        var offset=if(uploadId.isNotBlank())headOffset(config,projectId,uploadId)?:run{Files.deleteIfExists(state);uploadId="";0L}else 0L
        if(uploadId.isBlank()){
            val body="""{"fileName":${JsonPrimitive(fileName)},"sizeBytes":$total,"mediaType":${mediaType?.let(::JsonPrimitive)?:"null"}}"""
            val response=sendText(config,request(config,URI.create("${config.normalizedServerUrl}/api/v1/projects/$projectId/data-files/upload-sessions")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build())
            if(response.status !in 200..299)throw TaskCliHttpException(response.status,response.body.take(500));uploadId=Json.parseToJsonElement(response.body).jsonObject.getValue("uploadId").jsonPrimitive.content;Files.writeString(state,uploadId)
        }
        while(offset<total){
            val length=minOf(chunkSize,total-offset);var attempt=0
            while(true){try{
                val start=offset;val publisher=HttpRequest.BodyPublishers.ofInputStream{Files.newInputStream(file).also{it.skipNBytes(start)}.limited(length)}
                val response=client.send(request(config,URI.create("${config.normalizedServerUrl}/api/v1/projects/$projectId/data-files/upload-sessions/$uploadId")).header("Content-Type","application/offset+octet-stream").header("Upload-Offset",offset.toString()).method("PATCH",publisher).build(),HttpResponse.BodyHandlers.ofString())
                if(response.statusCode() !in 200..299)throw TaskCliHttpException(response.statusCode(),response.body().take(500))
                offset=response.headers().firstValue("Upload-Offset").orElse((offset+length).toString()).toLong();progress(offset,total);break
            }catch(failure:Exception){attempt++;if(attempt>=3)throw failure;Thread.sleep(250L shl attempt);offset=headOffset(config,projectId,uploadId)?:offset}}
        }
        val completed=sendText(config,request(config,URI.create("${config.normalizedServerUrl}/api/v1/projects/$projectId/data-files/upload-sessions/$uploadId/complete")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build());if(completed.status in 200..299)Files.deleteIfExists(state);System.err.println();return completed
    }

    override fun download(
        config: TaskServerConfig,
        projectId: Int,
        dataFileId: Int,
        target: Path,
        force: Boolean
    ): DataFileDownloadResult {
        val normalized = target.toAbsolutePath().normalize()
        require(force || !Files.exists(normalized)) { "Download target already exists: $normalized (use --force to replace)" }
        val parent = normalized.parent ?: throw IllegalArgumentException("Download target must have a parent directory.")
        Files.createDirectories(parent)
        val temporary = parent.resolve(".${normalized.fileName}.edb-part")
        val offset=if(Files.isRegularFile(temporary))Files.size(temporary) else 0L
        val request = HttpRequest.newBuilder(
            URI.create("${config.normalizedServerUrl}/api/v1/projects/$projectId/data-files/$dataFileId/download")
        )
            .timeout(Duration.ofMillis(config.requestTimeoutMs))
            .header("Accept", "application/octet-stream")
            .header("Authorization", "Bearer ${config.token}")
            .apply { if(offset>0)header("Range","bytes=$offset-") }
            .GET()
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TaskCliConnectionException("Download was interrupted.", failure)
        } catch (failure: Exception) {
            throw TaskCliConnectionException("Cannot download from ${config.normalizedServerUrl}: ${failure.message}", failure)
        }
        response.body().use { body ->
            if (response.statusCode() !in 200..299) {
                throw TaskCliHttpException(response.statusCode(), body.bufferedReader().readText().take(500))
            }
            try {
                if(offset>0&&response.statusCode()!=206)Files.deleteIfExists(temporary)
                val append=offset>0&&response.statusCode()==206
                val bytes = Files.newOutputStream(temporary,StandardOpenOption.CREATE,if(append)StandardOpenOption.APPEND else StandardOpenOption.TRUNCATE_EXISTING).use { output -> body.copyTo(output) }
                atomicMove(temporary, normalized, force)
                return DataFileDownloadResult(
                    normalized,
                    (if(append)offset else 0)+bytes,
                    response.headers().firstValue("Content-Type").orElse(null)
                )
            } catch(failure:Exception){throw failure}
        }
    }

    private fun request(config:TaskServerConfig,uri:URI)=HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(config.requestTimeoutMs)).header("Accept","application/json").header("Authorization","Bearer ${config.token}")
    private fun headOffset(config:TaskServerConfig,projectId:Int,id:String):Long?=runCatching{val response=client.send(request(config,URI.create("${config.normalizedServerUrl}/api/v1/projects/$projectId/data-files/upload-sessions/$id")).method("HEAD",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding());if(response.statusCode() in 200..299)response.headers().firstValue("Upload-Offset").orElse("0").toLong() else null}.getOrNull()

    private fun sendText(config: TaskServerConfig, request: HttpRequest): TaskHttpResponse = try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        TaskHttpResponse(response.statusCode(), response.body())
    } catch (failure: InterruptedException) {
        Thread.currentThread().interrupt()
        throw TaskCliConnectionException("Upload was interrupted.", failure)
    } catch (failure: Exception) {
        throw TaskCliConnectionException("Cannot upload to ${config.normalizedServerUrl}: ${failure.message}", failure)
    }

    private fun atomicMove(source: Path, target: Path, force: Boolean) {
        val options = buildList {
            add(StandardCopyOption.ATOMIC_MOVE)
            if (force) add(StandardCopyOption.REPLACE_EXISTING)
        }.toTypedArray()
        try {
            Files.move(source, target, *options)
        } catch (_: AtomicMoveNotSupportedException) {
            if (force) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            else Files.move(source, target)
        }
    }
}
