package me.moiclathy.edb.web.routes.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.config.snapshot.service.ConfigurationSnapshotService
import me.moiclathy.edb.core.service.ProjectService
import me.moiclathy.edb.web.security.requireUserId
import me.moiclathy.edb.web.security.username

@Serializable
data class CreateSnapshotRequest(val yaml: String, val description: String? = null)

fun Route.configurationSnapshotRoutes(
    snapshotService: ConfigurationSnapshotService,
    projectService: ProjectService
) {
    authenticate("auth-jwt") {
        route("/api/v1/projects/{projectId}/config/snapshots") {
            post {
                val projectId = call.snapshotProjectId() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@post
                projectService.requireWritableProject(projectId, userId)
                val request = call.receive<CreateSnapshotRequest>()
                val username = call.principal<JWTPrincipal>()!!.username()
                val snapshot = snapshotService.createSnapshot(
                    projectId, request.yaml, username, userId, request.description
                )
                call.respond(HttpStatusCode.Created, snapshot)
            }

            get {
                val projectId = call.snapshotProjectId() ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@get
                projectService.getProject(projectId, userId)
                call.respond(HttpStatusCode.OK, snapshotService.listSnapshots(projectId))
            }

            get("/latest") {
                val projectId = call.snapshotProjectId() ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@get
                projectService.getProject(projectId, userId)
                val latest = snapshotService.getLatestSnapshot(projectId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "No snapshot found"))
                call.respond(HttpStatusCode.OK, latest)
            }

            get("/count") {
                val projectId = call.snapshotProjectId() ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@get
                projectService.getProject(projectId, userId)
                call.respond(HttpStatusCode.OK, mapOf("count" to snapshotService.countSnapshots(projectId)))
            }
        }

        route("/api/v1/config/snapshots/{id}") {
            get {
                val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@get
                val snapshot = snapshotService.getSnapshotOrNull(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                projectService.getProject(snapshot.projectId, userId)
                call.respond(HttpStatusCode.OK, snapshot)
            }

            delete {
                val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@delete
                val snapshot = snapshotService.getSnapshotOrNull(id)
                    ?: return@delete call.respond(HttpStatusCode.NotFound)
                projectService.requireWritableProject(snapshot.projectId, userId)
                snapshotService.deleteSnapshot(id, userId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.snapshotProjectId() =
    parameters["projectId"]?.toIntOrNull()?.takeIf { it > 0 }
