package me.moiclathy.edb.web.routes.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import me.moiclathy.edb.core.config.metadata.service.ConfigurationMetadataService
import me.moiclathy.edb.core.service.ProjectService
import me.moiclathy.edb.web.dto.ConfigurationMetadataResponse
import me.moiclathy.edb.web.dto.CreateConfigurationMetadataRequest
import me.moiclathy.edb.web.dto.UpdateConfigurationMetadataRequest
import me.moiclathy.edb.web.security.requireUserId

fun Route.configurationMetadataRoutes(
    service: ConfigurationMetadataService,
    projectService: ProjectService
) {
    authenticate("auth-jwt") {
        route("/api/v1/projects/{projectId}/metadata") {
            get {
                val projectId = call.projectIdOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@get
                projectService.getProject(projectId, userId)
                val metadata = service.get(projectId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(HttpStatusCode.OK, ConfigurationMetadataResponse.from(metadata))
            }

            post {
                val projectId = call.projectIdOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@post
                projectService.requireWritableProject(projectId, userId)
                val request = call.receive<CreateConfigurationMetadataRequest>()
                val metadata = service.create(
                    projectId, request.description, request.category, request.tags, request.dataSource
                )
                call.respond(HttpStatusCode.Created, ConfigurationMetadataResponse.from(metadata))
            }

            put {
                val projectId = call.projectIdOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@put
                projectService.requireWritableProject(projectId, userId)
                val request = call.receive<UpdateConfigurationMetadataRequest>()
                val metadata = service.update(
                    projectId, request.description, request.category, request.tags, request.dataSource
                )
                call.respond(HttpStatusCode.OK, ConfigurationMetadataResponse.from(metadata))
            }

            delete {
                val projectId = call.projectIdOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@delete
                projectService.requireWritableProject(projectId, userId)
                service.delete(projectId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.projectIdOrNull() =
    parameters["projectId"]?.toIntOrNull()?.takeIf { it > 0 }
