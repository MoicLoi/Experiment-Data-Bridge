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
import me.moiclathy.edb.core.worker.docker.registry.*
import me.moiclathy.edb.web.security.requireRole

fun Route.dockerRegistryProfileAdminRoutes(service:DockerRegistryProfileService){authenticate("auth-jwt"){route("/api/v1/admin/docker-registries"){
 get{if(!admin())return@get;call.respond(RegistryProfilesResponse(service.list().map(RegistryProfileResponse::from)))}
 post{if(!admin())return@post;call.respond(HttpStatusCode.Created,RegistryProfileResponse.from(service.save(call.receive<SaveRegistryProfileRequest>().command())))}
 put("/{id}"){if(!admin())return@put;val id=id()?:return@put;val request=call.receive<SaveRegistryProfileRequest>();call.respond(RegistryProfileResponse.from(service.save(request.command(id))))}
 post("/{id}/default"){if(!admin())return@post;val id=id()?:return@post;call.respond(RegistryProfileResponse.from(service.setDefault(id)))}
 delete("/{id}"){if(!admin())return@delete;val id=id()?:return@delete;if(call.request.queryParameters["confirm"]!="true"){call.respond(HttpStatusCode.BadRequest,mapOf("message" to "confirm=true is required"));return@delete};service.delete(id);call.respond(HttpStatusCode.NoContent)}
}}}
private suspend fun RoutingContext.admin():Boolean{val principal=call.principal<JWTPrincipal>()?:return false.also{call.respond(HttpStatusCode.Unauthorized)};return requireRole(principal,UserRole.ADMIN)}
private suspend fun RoutingContext.id():String?=call.parameters["id"]?.takeIf(String::isNotBlank)?:null.also{call.respond(HttpStatusCode.BadRequest)}
@Serializable data class RegistryProfilesResponse(val items:List<RegistryProfileResponse>)
@Serializable data class RegistryProfileResponse(val id:String,val displayName:String,val registryUrl:String,val namespace:String?,val credentialRef:String?,val credentialConfigured:Boolean,val allowInsecure:Boolean,val isDefault:Boolean,val createdAt:String,val updatedAt:String){companion object{fun from(v:DockerRegistryProfile)=RegistryProfileResponse(v.id,v.displayName,v.registryUrl,v.namespace,v.credentialRef,v.credentialRef?.let(System::getenv)?.isNotBlank()==true,v.allowInsecure,v.isDefault,v.createdAt.toString(),v.updatedAt.toString())}}
@Serializable data class SaveRegistryProfileRequest(val id:String?=null,val displayName:String,val registryUrl:String,val namespace:String?=null,val credentialRef:String?=null,val allowInsecure:Boolean=false,val isDefault:Boolean=false){fun command(pathId:String?=null)=SaveDockerRegistryProfile(pathId?:requireNotNull(id){"id is required"},displayName,registryUrl,namespace,credentialRef,allowInsecure,isDefault)}
