package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.application.TaskRetryAdminService
import me.moiclathy.edb.web.dto.TaskResponse
import me.moiclathy.edb.web.security.requireRole
import me.moiclathy.edb.web.security.userId

fun Route.taskRetryAdminRoutes(service: TaskRetryAdminService) {
    authenticate("auth-jwt") {
        post("/api/v1/admin/tasks/{taskId}/requeue") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            if (!requireRole(principal, UserRole.ADMIN)) return@post
            val taskId = call.parameters["taskId"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Task ID must be a valid integer.")
            val task = service.requeue(taskId, principal.userId())
            call.respond(TaskResponse.from(task))
        }
    }
}
