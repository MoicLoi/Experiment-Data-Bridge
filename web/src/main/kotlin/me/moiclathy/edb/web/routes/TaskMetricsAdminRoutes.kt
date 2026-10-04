package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.metrics.TaskMetricsApplicationService
import me.moiclathy.edb.web.dto.TaskRuntimeMetricsResponse
import me.moiclathy.edb.web.security.requireRole

fun Route.taskMetricsAdminRoutes(service: TaskMetricsApplicationService) {
    authenticate("auth-jwt") {
        get("/api/v1/admin/task-metrics") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            if (!requireRole(principal, UserRole.ADMIN)) return@get
            call.respond(TaskRuntimeMetricsResponse.from(service.snapshot()))
        }
    }
}
