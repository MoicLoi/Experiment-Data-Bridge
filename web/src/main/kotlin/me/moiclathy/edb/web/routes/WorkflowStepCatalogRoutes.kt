package me.moiclathy.edb.web.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import me.moiclathy.edb.core.task.workflow.catalog.WorkflowStepCatalogService
import me.moiclathy.edb.web.dto.ErrorResponse
import me.moiclathy.edb.web.dto.WorkflowStepCatalogItemResponse
import me.moiclathy.edb.web.dto.WorkflowStepCatalogResponse

fun Route.workflowStepCatalogRoutes(service: WorkflowStepCatalogService) {
    authenticate("auth-jwt") {
        route("/api/v1/workflow-steps") {
            get {
                if (!call.requireCatalogPrincipal()) return@get
                call.respond(
                    WorkflowStepCatalogResponse(service.list().map(WorkflowStepCatalogItemResponse::from))
                )
            }

            get("/{stepType}") {
                if (!call.requireCatalogPrincipal()) return@get
                val (type, version) = call.catalogKey() ?: return@get
                call.respond(WorkflowStepCatalogItemResponse.from(service.require(type, version)))
            }

            get("/{stepType}/schema") {
                if (!call.requireCatalogPrincipal()) return@get
                val (type, version) = call.catalogKey() ?: return@get
                call.respondText(
                    service.requireSchema(type, version).toString(),
                    ContentType.parse("application/schema+json")
                )
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireCatalogPrincipal(): Boolean {
    if (principal<JWTPrincipal>() != null) return true
    respond(HttpStatusCode.Unauthorized)
    return false
}

private suspend fun io.ktor.server.application.ApplicationCall.catalogKey(): Pair<String, String>? {
    val type = parameters["stepType"]?.trim().orEmpty()
    val version = request.queryParameters["version"]?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: WorkflowStepCatalogService.DEFAULT_VERSION
    if (type.isEmpty()) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_WORKFLOW_STEP_TYPE", "Workflow step type is required."))
        return null
    }
    return type to version
}
