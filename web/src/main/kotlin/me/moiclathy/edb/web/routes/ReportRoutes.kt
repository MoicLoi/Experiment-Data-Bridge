package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.moiclathy.edb.core.datafile.report.ReportTaskApplicationService
import me.moiclathy.edb.web.dto.DataFileResponse
import me.moiclathy.edb.web.dto.ReportDefinitionResponse
import me.moiclathy.edb.web.dto.SubmitReportTaskRequest
import me.moiclathy.edb.web.dto.TaskResponse
import me.moiclathy.edb.web.security.requireUserId

fun Route.reportRoutes(service: ReportTaskApplicationService) {
    authenticate("auth-jwt") {
        route("/api/v1/projects/{projectId}/reports") {
            get("/definitions") {
                val projectId = call.reportPositiveId("projectId", "Project") ?: return@get
                val userId = call.requireUserId() ?: return@get
                call.respond(service.listDefinitions(projectId, userId).map(ReportDefinitionResponse::from))
            }

            post {
                val projectId = call.reportPositiveId("projectId", "Project") ?: return@post
                val userId = call.requireUserId() ?: return@post
                val request = call.receive<SubmitReportTaskRequest>()
                val task = service.submit(request.toCommand(projectId, userId))
                call.respond(HttpStatusCode.Accepted, TaskResponse.from(task))
            }
        }

        get("/api/v1/projects/{projectId}/data-files/{dataFileId}/reports") {
            val projectId = call.reportPositiveId("projectId", "Project") ?: return@get
            val dataFileId = call.reportPositiveId("dataFileId", "DataFile") ?: return@get
            val userId = call.requireUserId() ?: return@get
            call.respond(
                service.listArtifactsBySource(projectId, dataFileId, userId).map(DataFileResponse::from)
            )
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.reportPositiveId(
    parameter: String,
    label: String
): Int? {
    val value = parameters[parameter]?.toIntOrNull()
    if (value == null || value <= 0) {
        respond(HttpStatusCode.BadRequest, "$label ID must be a positive integer.")
        return null
    }
    return value
}
