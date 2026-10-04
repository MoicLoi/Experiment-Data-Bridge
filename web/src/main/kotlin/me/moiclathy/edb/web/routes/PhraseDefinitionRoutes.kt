package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import me.moiclathy.edb.core.task.workflow.phrase.ProjectPhraseDefinitionService
import me.moiclathy.edb.core.task.workflow.phrase.PhraseDefinitionTaskService
import me.moiclathy.edb.web.dto.*
import me.moiclathy.edb.web.security.requireUserId

fun Route.phraseDefinitionRoutes(service:ProjectPhraseDefinitionService,tasks:PhraseDefinitionTaskService){authenticate("auth-jwt"){
    get("/api/v1/projects/{projectId}/phrase-definitions"){val projectId=call.parameters["projectId"]?.toIntOrNull()?.takeIf{it>0}?:return@get call.respond(HttpStatusCode.BadRequest,"Project ID must be positive.");val userId=call.requireUserId()?:return@get;val page=call.request.queryParameters["page"]?.toIntOrNull()?:0;val size=call.request.queryParameters["size"]?.toIntOrNull()?:20;val result=service.list(projectId,userId,page,size);call.respond(PhraseDefinitionPageResponse(result.items.map(PhraseDefinitionResponse::from),result.page,result.size,result.total))}
    post("/api/v1/projects/{projectId}/phrase-definitions"){val projectId=call.parameters["projectId"]?.toIntOrNull()?.takeIf{it>0}?:return@post call.respond(HttpStatusCode.BadRequest,"Project ID must be positive.");val userId=call.requireUserId()?:return@post;val request=call.receive<SavePhraseDefinitionRequest>();call.respond(HttpStatusCode.Created,PhraseDefinitionResponse.from(service.create(projectId,request.definitionId,request.document,userId)))}
    get("/api/v1/projects/{projectId}/phrase-definitions/{definitionId}/latest"){val projectId=call.parameters["projectId"]?.toIntOrNull()?.takeIf{it>0}?:return@get call.respond(HttpStatusCode.BadRequest,"Project ID must be positive.");val userId=call.requireUserId()?:return@get;val definitionId=call.parameters["definitionId"].orEmpty();val value=service.latest(projectId,definitionId,userId)?:return@get call.respond(HttpStatusCode.NotFound,"Phrase definition was not found.");call.respond(PhraseDefinitionResponse.from(value))}
    patch("/api/v1/projects/{projectId}/phrase-definitions/{definitionId}"){val projectId=call.parameters["projectId"]?.toIntOrNull()?.takeIf{it>0}?:return@patch call.respond(HttpStatusCode.BadRequest,"Project ID must be positive.");val userId=call.requireUserId()?:return@patch;val definitionId=call.parameters["definitionId"].orEmpty();val request=call.receive<RenamePhraseDefinitionRequest>();call.respond(mapOf("renamedVersions" to service.rename(projectId,definitionId,request.newDefinitionId,userId)))}
    delete("/api/v1/projects/{projectId}/phrase-definitions/{definitionId}/versions/{version}"){val projectId=call.parameters["projectId"]?.toIntOrNull()?.takeIf{it>0}?:return@delete call.respond(HttpStatusCode.BadRequest,"Project ID must be positive.");val version=call.parameters["version"]?.toIntOrNull()?.takeIf{it>0}?:return@delete call.respond(HttpStatusCode.BadRequest,"Version must be positive.");val userId=call.requireUserId()?:return@delete;service.delete(projectId,call.parameters["definitionId"].orEmpty(),version,userId);call.respond(HttpStatusCode.NoContent)}
    post("/api/v1/projects/{projectId}/phrase-definitions/{definitionId}/submit"){val projectId=call.parameters["projectId"]?.toIntOrNull()?.takeIf{it>0}?:return@post call.respond(HttpStatusCode.BadRequest,"Project ID must be positive.");val userId=call.requireUserId()?:return@post;val request=call.receive<SubmitPhraseDefinitionRequest>();val task=tasks.submit(request.toCommand(projectId,call.parameters["definitionId"].orEmpty(),userId));call.respond(HttpStatusCode.Accepted,TaskResponse.from(task))}
}}
