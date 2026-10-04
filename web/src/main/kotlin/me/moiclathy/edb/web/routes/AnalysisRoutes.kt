package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.moiclathy.edb.core.datafile.analysis.AnalysisTaskApplicationService
import me.moiclathy.edb.core.datafile.analysis.DataFileTabularProfileService
import kotlinx.serialization.Serializable
import me.moiclathy.edb.web.dto.AnalysisHandlerResponse
import me.moiclathy.edb.web.dto.DataFileResponse
import me.moiclathy.edb.web.dto.SubmitAnalysisTaskRequest
import me.moiclathy.edb.web.dto.TaskResponse
import me.moiclathy.edb.web.security.requireUserId

fun Route.analysisRoutes(service: AnalysisTaskApplicationService, profiles: DataFileTabularProfileService? = null) {
    authenticate("auth-jwt") {
        get("/api/v1/projects/{projectId}/analysis-handlers") {
            val projectId = call.analysisPositiveId("projectId", "Project") ?: return@get
            val userId = call.requireUserId() ?: return@get
            call.respond(service.listHandlers(projectId, userId).map(AnalysisHandlerResponse::from))
        }

        post("/api/v1/projects/{projectId}/analyses") {
            val projectId = call.analysisPositiveId("projectId", "Project") ?: return@post
            val userId = call.requireUserId() ?: return@post
            val request = call.receive<SubmitAnalysisTaskRequest>()
            val task = service.submit(request.toCommand(projectId, userId))
            call.respond(HttpStatusCode.Accepted, TaskResponse.from(task))
        }

        get("/api/v1/projects/{projectId}/data-files/{dataFileId}/analyses") {
            val projectId = call.analysisPositiveId("projectId", "Project") ?: return@get
            val dataFileId = call.analysisPositiveId("dataFileId", "DataFile") ?: return@get
            val userId = call.requireUserId() ?: return@get
            call.respond(
                service.listArtifactsBySource(projectId, dataFileId, userId).map(DataFileResponse::from)
            )
        }

        if (profiles != null) {
            get("/api/v1/projects/{projectId}/data-files/{dataFileId}/tabular-profile") {
                val projectId = call.analysisPositiveId("projectId", "Project") ?: return@get
                val dataFileId = call.analysisPositiveId("dataFileId", "DataFile") ?: return@get
                val userId = call.requireUserId() ?: return@get
                val profile = profiles.profile(projectId, dataFileId, userId)
                call.respond(TabularProfileResponse(profile.columns.map { TabularColumnResponse(it.name, it.type, it.values) }))
            }
        }
    }
}

@Serializable private data class TabularProfileResponse(val columns: List<TabularColumnResponse>)
@Serializable private data class TabularColumnResponse(val name: String, val type: String, val values: List<String>)

private suspend fun io.ktor.server.application.ApplicationCall.analysisPositiveId(
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
