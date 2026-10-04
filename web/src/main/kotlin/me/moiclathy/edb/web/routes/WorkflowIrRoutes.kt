package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.workflow.application.WorkflowIrApplicationService
import me.moiclathy.edb.web.dto.WorkflowIrCompilationResponse
import me.moiclathy.edb.web.security.requireRole

fun Route.workflowIrRoutes(service: WorkflowIrApplicationService) {
    authenticate("auth-jwt") {
        route("/api/v1/workflows") {
            post("/validate") {
                val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val result = service.validate(call.receiveText())
                call.respond(WorkflowIrCompilationResponse.from(result))
            }

            post("/compile") {
                val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                if (!requireRole(principal, UserRole.ADMIN)) return@post
                val result = service.compileAndRegister(call.receiveText())
                call.respond(HttpStatusCode.Created, WorkflowIrCompilationResponse.from(result))
            }
        }
    }
}
