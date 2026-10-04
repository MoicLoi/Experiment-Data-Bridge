package me.moiclathy.edb.web.routes.project

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.moiclathy.edb.core.service.ProjectConfigurationImportService
import me.moiclathy.edb.web.security.requireUserId

data class ImportConfigurationRequest(
    val yaml: String
)

fun Route.projectConfigurationImportRoutes(

    importService: ProjectConfigurationImportService

) {

    authenticate("auth-jwt") {

        post("/api/v1/projects/{id}/configuration/import") {

            val projectId =
                call.parameters["id"]
                    ?.toIntOrNull()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest
                    )

            val userId =
                call.requireUserId()
                    ?: return@post

            val request =
                call.receive<ImportConfigurationRequest>()

            val principal =
                call.principal<JWTPrincipal>()

            val author =
                principal
                    ?.payload
                    ?.subject
                    ?: "unknown"

            val result =
                importService.importConfiguration(
                    projectId = projectId,
                    userId = userId,
                    yaml = request.yaml,
                    author = author
                )

            call.respond(result)
        }
    }
}