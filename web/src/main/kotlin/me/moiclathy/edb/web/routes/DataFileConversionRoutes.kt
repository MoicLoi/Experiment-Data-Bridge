package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionApplicationService
import me.moiclathy.edb.web.dto.DataFileResponse
import me.moiclathy.edb.web.dto.DataFileConversionCapabilityResponse
import me.moiclathy.edb.web.dto.SubmitDataFileConversionRequest
import me.moiclathy.edb.web.dto.TaskResponse
import me.moiclathy.edb.web.security.requireUserId

fun Route.dataFileConversionRoutes(service: DataFileConversionApplicationService) {
    authenticate("auth-jwt") {
        get("/api/v1/projects/{projectId}/data-files/{dataFileId}/conversion-targets") {
            val projectId = call.positivePathId("projectId", "Project") ?: return@get
            val dataFileId = call.positivePathId("dataFileId", "DataFile") ?: return@get
            val userId = call.requireUserId() ?: return@get
            call.respond(
                service.capabilities(projectId, dataFileId, userId)
                    .map(DataFileConversionCapabilityResponse::from)
            )
        }

        route("/api/v1/projects/{projectId}/data-files/{dataFileId}/conversions") {
            post {
                val projectId = call.positivePathId("projectId", "Project") ?: return@post
                val dataFileId = call.positivePathId("dataFileId", "DataFile") ?: return@post
                val userId = call.requireUserId() ?: return@post
                val request = call.receive<SubmitDataFileConversionRequest>()
                val task = service.submit(request.toCommand(projectId, dataFileId, userId))
                call.respond(HttpStatusCode.Accepted, TaskResponse.from(task))
            }

            get {
                val projectId = call.positivePathId("projectId", "Project") ?: return@get
                val dataFileId = call.positivePathId("dataFileId", "DataFile") ?: return@get
                val userId = call.requireUserId() ?: return@get
                call.respond(
                    service.listDerived(projectId, dataFileId, userId).map(DataFileResponse::from)
                )
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.positivePathId(
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
