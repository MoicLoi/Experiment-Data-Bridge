package me.moiclathy.edb.web.routes
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.worker.docker.*
import me.moiclathy.edb.web.security.requireRole

fun Route.dockerWorkerRebuildAdminRoutes(service:DockerWorkerRebuildApplicationService){authenticate("auth-jwt"){route("/api/v1/admin/docker-workers/{workerId}/rebuild"){
 post("/preview"){if(!rebuildAdmin())return@post;val worker=call.parameters["workerId"]?:return@post call.respond(HttpStatusCode.BadRequest);val r=call.receive<RebuildRequest>();call.respond(RebuildPreviewResponse.from(service.preview(worker,r.registryProfileId,r.repository,r.tag,r.useLocalImage)))}
 post{if(!rebuildAdmin())return@post;val worker=call.parameters["workerId"]?:return@post call.respond(HttpStatusCode.BadRequest);val r=call.receive<RebuildRequest>();val token=requireNotNull(r.confirmationToken){"confirmationToken is required"};call.respond(RebuildResultResponse.from(service.rebuild(worker,r.registryProfileId,r.repository,r.tag,token,r.useLocalImage)))}
 get("/progress"){if(!rebuildAdmin())return@get;val worker=call.parameters["workerId"]?:return@get call.respond(HttpStatusCode.BadRequest);val value=service.progress(worker)?:return@get call.respond(HttpStatusCode.NoContent);call.respond(RebuildProgressResponse(value.workerId,value.stage.name,value.targetImage,value.startedAt.toString(),value.updatedAt.toString(),value.result?.let(RebuildResultResponse::from)))}
}}}
private suspend fun RoutingContext.rebuildAdmin():Boolean{val p=call.principal<JWTPrincipal>()?:return false.also{call.respond(HttpStatusCode.Unauthorized)};return requireRole(p,UserRole.ADMIN)}
@Serializable data class RebuildRequest(val registryProfileId:String="",val repository:String,val tag:String,val useLocalImage:Boolean=false,val confirmationToken:String?=null)
@Serializable data class RebuildPreviewResponse(val workerId:String,val currentImage:String,val targetImage:String,val registryProfileId:String,val credentialConfigured:Boolean,val localImage:Boolean,val confirmationToken:String){companion object{fun from(v:DockerWorkerRebuildPreview)=RebuildPreviewResponse(v.workerId,v.currentImage,v.targetImage,v.registryProfileId,v.credentialConfigured,v.localImage,v.confirmationToken)}}
@Serializable data class RebuildResultResponse(val workerId:String,val previousImage:String?,val targetImage:String,val stage:String,val rolledBack:Boolean,val containerId:String?,val completedAt:String,val failure:String?){companion object{fun from(v:DockerWorkerRebuildResult)=RebuildResultResponse(v.workerId,v.previousImage,v.targetImage,v.stage.name,v.rolledBack,v.containerId,v.completedAt.toString(),v.failure)}}
@Serializable data class RebuildProgressResponse(val workerId:String,val stage:String,val targetImage:String,val startedAt:String,val updatedAt:String,val result:RebuildResultResponse?)
