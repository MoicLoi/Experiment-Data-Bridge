package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import me.moiclathy.edb.core.task.workflow.template.WorkflowTemplateRegistry
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCodec
import me.moiclathy.edb.web.dto.ErrorResponse
import me.moiclathy.edb.web.dto.WorkflowTemplateCatalogResponse
import me.moiclathy.edb.web.dto.WorkflowTemplateResponse

fun Route.workflowTemplateRoutes(registry: WorkflowTemplateRegistry, codec: WorkflowIrCodec) {
    authenticate("auth-jwt") {
        route("/api/v1/workflow-templates") {
            get {
                if (call.principal<JWTPrincipal>() == null) return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(WorkflowTemplateCatalogResponse(registry.list().map { WorkflowTemplateResponse.from(it, codec) }))
            }
            get("/{templateId}") {
                if (call.principal<JWTPrincipal>() == null) return@get call.respond(HttpStatusCode.Unauthorized)
                val id = call.parameters["templateId"]?.trim().orEmpty()
                val version = call.request.queryParameters["version"]?.trim().orEmpty().ifBlank { "1.0.0" }
                val template = runCatching { registry.require(id, version) }.getOrElse {
                    return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("WORKFLOW_TEMPLATE_NOT_FOUND", it.message ?: "Workflow template not found."))
                }
                call.respond(WorkflowTemplateResponse.from(template, codec))
            }
        }
    }
}
