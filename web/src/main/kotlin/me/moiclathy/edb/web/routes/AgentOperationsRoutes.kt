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
import me.moiclathy.edb.agent.application.AgentOperationsService
import me.moiclathy.edb.agent.application.AgentRetentionManager
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.web.dto.AgentMetricsResponse
import me.moiclathy.edb.web.dto.AgentRetentionCleanupResponse
import me.moiclathy.edb.web.dto.AgentRetentionEstimateResponse
import me.moiclathy.edb.web.dto.AgentRetentionOverviewResponse
import me.moiclathy.edb.web.dto.AgentRetentionPolicyResponse
import me.moiclathy.edb.web.security.requireRole

fun Route.agentOperationsAdminRoutes(service: AgentOperationsService, manager: AgentRetentionManager) {
    authenticate("auth-jwt") {
        get("/api/v1/admin/agent-metrics") {
            if (!requireAgentAdmin()) return@get
            call.respond(AgentMetricsResponse.from(service.metrics()))
        }
        route("/api/v1/admin/agent-retention") {
            get {
                if (!requireAgentAdmin()) return@get
                val state = manager.state.value
                call.respond(AgentRetentionOverviewResponse(
                    AgentRetentionPolicyResponse.from(service.retentionPolicy),
                    state.lastResult?.let(AgentRetentionCleanupResponse::from),
                    state.started, state.running, state.lastStartedAt?.toString(), state.lastFailure
                ))
            }
            get("/estimate") {
                if (!requireAgentAdmin()) return@get
                call.respond(AgentRetentionEstimateResponse.from(service.estimate()))
            }
            post("/cleanup") {
                if (!requireAgentAdmin()) return@post
                call.respond(AgentRetentionCleanupResponse.from(manager.runNow()))
            }
            get("/last-result") {
                if (!requireAgentAdmin()) return@get
                val result = manager.state.value.lastResult
                    ?: return@get call.respond(HttpStatusCode.NoContent)
                call.respond(AgentRetentionCleanupResponse.from(result))
            }
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.requireAgentAdmin(): Boolean {
    val principal = call.principal<JWTPrincipal>() ?: run {
        call.respond(HttpStatusCode.Unauthorized)
        return false
    }
    return requireRole(principal, UserRole.ADMIN)
}
