package me.moiclathy.edb.web.routes.project

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.moiclathy.edb.core.config.merge.service.ConfigurationMergeService
import me.moiclathy.edb.core.config.snapshot.service.ConfigurationSnapshotService
import me.moiclathy.edb.core.service.ProjectService
import me.moiclathy.edb.web.dto.ConfigurationMergeConflictResponse
import me.moiclathy.edb.web.dto.ConfigurationMergeConflictResponseBody
import me.moiclathy.edb.web.dto.ConfigurationMergeRequest
import me.moiclathy.edb.web.dto.ConfigurationMergeResponse
import me.moiclathy.edb.web.security.requireUserId

fun Route.projectConfigurationMergeRoutes(
    mergeService: ConfigurationMergeService,
    snapshotService: ConfigurationSnapshotService,
    projectService: ProjectService
) {
    authenticate("auth-jwt") {
        route("/api/v1/projects/{projectId}/config/merge") {
            post {
                val projectId = call.parameters["projectId"]?.toIntOrNull()?.takeIf { it > 0 }
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@post
                val request = call.receive<ConfigurationMergeRequest>()
                val source = snapshotService.getSnapshotOrNull(request.sourceSnapshotId)
                    ?: return@post call.respond(HttpStatusCode.NotFound)
                val target = snapshotService.getSnapshotOrNull(request.targetSnapshotId)
                    ?: return@post call.respond(HttpStatusCode.NotFound)
                if (source.projectId != projectId || target.projectId != projectId) {
                    return@post call.respond(HttpStatusCode.NotFound)
                }
                projectService.requireWritableProject(projectId, userId)
                val result = mergeService.mergeSnapshots(
                    request.targetSnapshotId, request.sourceSnapshotId, userId, request.strategy
                )
                if (result.isSuccess) {
                    call.respond(HttpStatusCode.OK, ConfigurationMergeResponse(
                        result.targetSnapshot.id!!, result.sourceSnapshot.id!!, result.mergedYaml!!
                    ))
                } else {
                    call.respond(HttpStatusCode.Conflict, ConfigurationMergeConflictResponseBody(
                        result.targetSnapshot.id!!,
                        result.sourceSnapshot.id!!,
                        result.conflicts.map { ConfigurationMergeConflictResponse(
                            it.path, it.baseValue, it.sourceValue, it.targetValue, it.reason
                        ) }
                    ))
                }
            }
        }
    }
}
