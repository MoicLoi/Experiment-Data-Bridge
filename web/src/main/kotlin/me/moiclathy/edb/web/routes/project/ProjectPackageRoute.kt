package me.moiclathy.edb.web.routes.project

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import me.moiclathy.edb.core.service.ProjectConfigurationExportService
import me.moiclathy.edb.web.security.requireUserId

fun Route.projectPackageRoute(
    exportService: ProjectConfigurationExportService
) {
    authenticate("auth-jwt") {
        route("/api/v1/projects/{id}") {
            get("/package") {
                val projectId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val userId = call.requireUserId() ?: return@get
                val bytes = exportService.exportPackage(projectId, userId)

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    """attachment; filename="project.edb"""
                )
                call.respondBytes(bytes, ContentType.Application.OctetStream)
            }
        }
    }
}
