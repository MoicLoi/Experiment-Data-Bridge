package me.moiclathy.edb.web.routes

import io.ktor.server.auth.authenticate
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.moiclathy.edb.agent.provider.AgentModelProfileCatalogService
import me.moiclathy.edb.web.dto.AgentModelProfileCatalogResponse
import me.moiclathy.edb.web.dto.AgentModelProfileResponse
import me.moiclathy.edb.web.dto.AgentProviderStatusListResponse
import me.moiclathy.edb.web.dto.AgentProviderStatusResponse
import me.moiclathy.edb.web.dto.AgentModelMetricsListResponse
import me.moiclathy.edb.web.dto.AgentModelMetricsResponse
import me.moiclathy.edb.web.security.requireUserId
import me.moiclathy.edb.web.security.requireRole
import me.moiclathy.edb.core.security.UserRole

fun Route.agentModelProfileRoutes(catalog: AgentModelProfileCatalogService) {
    authenticate("auth-jwt") {
        get("/api/v1/agent-model-profiles") {
            call.requireUserId() ?: return@get
            call.respond(AgentModelProfileCatalogResponse(
                defaultProfileId = catalog.defaultProfileId,
                items = catalog.list().map { profile ->
                    AgentModelProfileResponse(
                        id = profile.id,
                        providerId = profile.providerId,
                        modelName = profile.modelName,
                        toolCallingEnabled = profile.toolCallingEnabled,
                        structuredOutputEnabled = profile.structuredOutputEnabled,
                        builtIn = profile.builtIn,
                        default = profile.id == catalog.defaultProfileId
                    )
                }
            ))
        }
    }
}

fun Route.agentProviderAdminRoutes(catalog: AgentModelProfileCatalogService) {
    authenticate("auth-jwt") {
        get("/api/v1/admin/agent-providers") {
            if (!requireProviderAdmin()) return@get
            call.respond(AgentProviderStatusListResponse(
                catalog.providerStatuses().map(AgentProviderStatusResponse::from)
            ))
        }
        get("/api/v1/admin/agent-model-metrics") {
            if (!requireProviderAdmin()) return@get
            call.respond(AgentModelMetricsListResponse(
                catalog.providerStatuses().map(AgentModelMetricsResponse::from)
            ))
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.requireProviderAdmin(): Boolean {
    val principal = call.principal<JWTPrincipal>() ?: run {
        call.respond(HttpStatusCode.Unauthorized)
        return false
    }
    return requireRole(principal, UserRole.ADMIN)
}
