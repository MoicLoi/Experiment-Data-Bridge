package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.retention.TaskRetentionAdminService
import me.moiclathy.edb.web.dto.TaskCleanupResultResponse
import me.moiclathy.edb.web.dto.TaskRetentionEstimateResponse
import me.moiclathy.edb.web.dto.TaskRetentionOverviewResponse
import me.moiclathy.edb.web.dto.TaskRetentionPolicyResponse
import me.moiclathy.edb.web.dto.TaskRetentionStateResponse
import me.moiclathy.edb.web.security.requireRole

fun Route.taskRetentionAdminRoutes(service: TaskRetentionAdminService) {
    authenticate("auth-jwt") {
        route("/api/v1/admin/task-retention") {
            get {
                if (!requireAdmin()) return@get
                call.respond(
                    TaskRetentionOverviewResponse(
                        policy = TaskRetentionPolicyResponse.from(service.policy),
                        state = TaskRetentionStateResponse.from(service.state())
                    )
                )
            }

            get("/estimate") {
                if (!requireAdmin()) return@get
                call.respond(TaskRetentionEstimateResponse.from(service.estimate()))
            }

            post("/cleanup") {
                if (!requireAdmin()) return@post
                call.respond(TaskCleanupResultResponse.from(service.cleanup()))
            }

            get("/last-result") {
                if (!requireAdmin()) return@get
                call.respond(TaskRetentionStateResponse.from(service.state()))
            }
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.requireAdmin(): Boolean {
    val principal = call.principal<JWTPrincipal>()
        ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return false
        }
    return requireRole(principal, UserRole.ADMIN)
}
