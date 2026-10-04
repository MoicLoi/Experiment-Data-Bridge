package me.moiclathy.edb.web.routes

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveStream
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.patch
import io.ktor.server.routing.head
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import me.moiclathy.edb.core.datafile.application.DataFileApplicationService
import me.moiclathy.edb.core.datafile.application.UploadDataFileCommand
import me.moiclathy.edb.web.dto.DataFileResponse
import me.moiclathy.edb.web.dto.CreateResumableUploadRequest
import me.moiclathy.edb.web.dto.ResumableUploadResponse
import me.moiclathy.edb.core.datafile.upload.ResumableUploadService
import me.moiclathy.edb.web.security.requireUserId

fun Route.dataFileRoutes(service: DataFileApplicationService, resumable: ResumableUploadService? = null) {
    authenticate("auth-jwt") {
        route("/api/v1/projects/{projectId}/data-files") {
            post {
                val projectId = call.projectId() ?: return@post
                val userId = call.requireUserId() ?: return@post
                var response: DataFileResponse? = null
                call.receiveMultipart().forEachPart { part ->
                    try {
                        if (part is PartData.FileItem && response == null) {
                            val uploaded = part.provider().toInputStream().use { input ->
                                service.upload(
                                    UploadDataFileCommand(
                                        projectId = projectId,
                                        userId = userId,
                                        originalFileName = part.originalFileName ?: "upload.bin",
                                        input = input,
                                        declaredMediaType = part.contentType?.toString()
                                    )
                                )
                            }
                            response = DataFileResponse.from(uploaded)
                        }
                    } finally {
                        part.dispose()
                    }
                }
                call.respond(
                    HttpStatusCode.Created,
                    response ?: throw IllegalArgumentException("Multipart request must contain a file part.")
                )
            }

            if(resumable!=null) route("/upload-sessions") {
                post {
                    val projectId=call.projectId()?:return@post;val userId=call.requireUserId()?:return@post;val request=call.receive<CreateResumableUploadRequest>()
                    val session=resumable.create(projectId,userId,request.fileName,request.mediaType,request.sizeBytes)
                    call.response.header(HttpHeaders.Location,"/api/v1/projects/$projectId/data-files/upload-sessions/${session.id}")
                    call.response.header("Upload-Offset",session.offsetBytes.toString())
                    call.respond(HttpStatusCode.Created,ResumableUploadResponse.from(session))
                }
                head("/{uploadId}") {
                    val projectId=call.projectId()?:return@head;val userId=call.requireUserId()?:return@head;val value=resumable.get(projectId,userId,call.parameters["uploadId"].orEmpty())
                    call.response.header("Upload-Offset",value.offsetBytes.toString());call.response.header("Upload-Length",value.totalBytes.toString());call.response.header("Upload-Status",value.status.name);call.respond(HttpStatusCode.NoContent)
                }
                patch("/{uploadId}") {
                    val projectId=call.projectId()?:return@patch;val userId=call.requireUserId()?:return@patch;val offset=call.request.headers["Upload-Offset"]?.toLongOrNull()?:return@patch call.respond(HttpStatusCode.BadRequest,"Upload-Offset header is required.")
                    val value=call.receiveStream().use{resumable.append(projectId,userId,call.parameters["uploadId"].orEmpty(),offset,it)}
                    call.response.header("Upload-Offset",value.offsetBytes.toString());call.respond(HttpStatusCode.NoContent)
                }
                post("/{uploadId}/complete") {
                    val projectId=call.projectId()?:return@post;val userId=call.requireUserId()?:return@post
                    call.respond(HttpStatusCode.Created,DataFileResponse.from(resumable.complete(projectId,userId,call.parameters["uploadId"].orEmpty())))
                }
                delete("/{uploadId}") {
                    val projectId=call.projectId()?:return@delete;val userId=call.requireUserId()?:return@delete;resumable.cancel(projectId,userId,call.parameters["uploadId"].orEmpty());call.response.status(HttpStatusCode.NoContent)
                }
            }

            get {
                val projectId = call.projectId() ?: return@get
                val userId = call.requireUserId() ?: return@get
                call.respond(service.list(projectId, userId).map(DataFileResponse::from))
            }

            get("/{dataFileId}") {
                val projectId = call.projectId() ?: return@get
                val dataFileId = call.dataFileId() ?: return@get
                val userId = call.requireUserId() ?: return@get
                call.respond(DataFileResponse.from(service.get(projectId, dataFileId, userId)))
            }

            delete("/{dataFileId}") {
                val projectId = call.projectId() ?: return@delete
                val dataFileId = call.dataFileId() ?: return@delete
                val userId = call.requireUserId() ?: return@delete
                call.respond(DataFileResponse.from(service.delete(projectId, dataFileId, userId)))
            }

            get("/{dataFileId}/download") {
                val projectId = call.projectId() ?: return@get
                val dataFileId = call.dataFileId() ?: return@get
                val userId = call.requireUserId() ?: return@get
                val download = service.openDownload(projectId, dataFileId, userId)
                val file = download.dataFile
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, file.originalFileName)
                        .toString()
                )
                val total=file.sizeBytes
                call.response.header(HttpHeaders.AcceptRanges,"bytes")
                val range=total?.let{parseByteRange(call.request.headers[HttpHeaders.Range],it)}
                val start=range?.first?:0L;val length=range?.let{it.last-it.first+1}?:total
                length?.let { call.response.header(HttpHeaders.ContentLength, it.toString()) }
                if(range!=null)call.response.header(HttpHeaders.ContentRange,"bytes ${range.first}-${range.last}/$total")
                val contentType = runCatching {
                    file.mediaType?.let(ContentType::parse)
                }.getOrNull() ?: ContentType.Application.OctetStream
                call.respondOutputStream(contentType,if(range==null)HttpStatusCode.OK else HttpStatusCode.PartialContent) {
                    download.input.use { input -> if(start>0)input.skipNBytes(start);copyLimited(input,this,length) }
                }
            }
        }
    }
}

private fun parseByteRange(header:String?,total:Long):LongRange?{if(header==null)return null;require(header.startsWith("bytes=")&&!header.removePrefix("bytes=").contains(',')){"Only one byte range is supported."};val value=header.removePrefix("bytes=");val (left,right)=value.split('-',limit=2).let{it[0] to it.getOrElse(1){""}};val range=if(left.isBlank()){val suffix=right.toLong();(total-suffix.coerceAtMost(total))..(total-1)}else{val start=left.toLong();start..(if(right.isBlank())total-1 else right.toLong().coerceAtMost(total-1))};require(range.first in 0 until total&&range.last>=range.first){"Requested range is not satisfiable."};return range}
private fun copyLimited(input:java.io.InputStream,output:java.io.OutputStream,length:Long?){if(length==null){input.copyTo(output);return};var remaining=length;val buffer=ByteArray(DEFAULT_BUFFER_SIZE);while(remaining>0){val read=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());if(read<0)break;output.write(buffer,0,read);remaining-=read}}

private suspend fun io.ktor.server.application.ApplicationCall.projectId(): Int? {
    val value = parameters["projectId"]?.toIntOrNull()
    if (value == null || value <= 0) {
        respond(HttpStatusCode.BadRequest, "Project ID must be a positive integer.")
        return null
    }
    return value
}

private suspend fun io.ktor.server.application.ApplicationCall.dataFileId(): Int? {
    val value = parameters["dataFileId"]?.toIntOrNull()
    if (value == null || value <= 0) {
        respond(HttpStatusCode.BadRequest, "DataFile ID must be a positive integer.")
        return null
    }
    return value
}
