package me.moiclathy.edb.web.routes.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import me.moiclathy.edb.core.config.compare.service.ConfigurationCompareService
import me.moiclathy.edb.core.config.snapshot.service.ConfigurationSnapshotService
import me.moiclathy.edb.core.service.ProjectService
import me.moiclathy.edb.web.dto.ConfigurationCompareResponse
import me.moiclathy.edb.web.security.requireUserId

fun Route.configurationCompareRoutes(
    compareService: ConfigurationCompareService,
    snapshotService: ConfigurationSnapshotService,
    projectService: ProjectService
) {
    authenticate("auth-jwt") {
        route("/api/v1/projects/{projectId}/config/snapshots") {
            get("/compare") {
                val projectId = call.parameters["projectId"]?.toIntOrNull()?.takeIf { it > 0 }
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val sourceId = call.request.queryParameters["sourceSnapshotId"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val targetId = call.request.queryParameters["targetSnapshotId"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@get
                val source = snapshotService.getSnapshotOrNull(sourceId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val target = snapshotService.getSnapshotOrNull(targetId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                if (source.projectId != projectId || target.projectId != projectId) {
                    return@get call.respond(HttpStatusCode.NotFound)
                }
                projectService.getProject(projectId, userId)
                call.respond(HttpStatusCode.OK, ConfigurationCompareResponse.from(compareService.compare(sourceId, targetId, userId)))
            }
        }
    }
}
