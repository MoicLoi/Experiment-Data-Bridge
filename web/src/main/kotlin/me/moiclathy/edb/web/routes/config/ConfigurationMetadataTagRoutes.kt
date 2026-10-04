package me.moiclathy.edb.web.routes.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.moiclathy.edb.core.config.metadata.service.ConfigurationMetadataService
import me.moiclathy.edb.core.service.ProjectService
import me.moiclathy.edb.web.dto.ConfigurationMetadataTagRequest
import me.moiclathy.edb.web.dto.ConfigurationMetadataTagsResponse
import me.moiclathy.edb.web.security.requireUserId

fun Route.configurationMetadataTagRoutes(
    service: ConfigurationMetadataService,
    projectService: ProjectService
) {
    authenticate("auth-jwt") {
        route("/api/v1/projects/{projectId}/metadata/tags") {
            get {
                val projectId = call.tagProjectIdOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@get
                projectService.getProject(projectId, userId)
                call.respond(HttpStatusCode.OK, ConfigurationMetadataTagsResponse(projectId, service.listTags(projectId)))
            }

            post {
                val projectId = call.tagProjectIdOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@post
                projectService.requireWritableProject(projectId, userId)
                val metadata = service.addTag(projectId, call.receive<ConfigurationMetadataTagRequest>().tag)
                call.respond(HttpStatusCode.OK, ConfigurationMetadataTagsResponse(projectId, metadata.tags))
            }

            delete("/{tag}") {
                val projectId = call.tagProjectIdOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@delete
                projectService.requireWritableProject(projectId, userId)
                val tag = call.parameters["tag"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val metadata = service.removeTag(projectId, tag)
                call.respond(HttpStatusCode.OK, ConfigurationMetadataTagsResponse(projectId, metadata.tags))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.tagProjectIdOrNull() =
    parameters["projectId"]?.toIntOrNull()?.takeIf { it > 0 }
