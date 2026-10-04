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
import me.moiclathy.edb.core.datafile.lifecycle.DataFileOrphanCleanupAdminService
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.web.dto.DataFileOrphanCleanupEstimateResponse
import me.moiclathy.edb.web.dto.DataFileOrphanCleanupOverviewResponse
import me.moiclathy.edb.web.dto.DataFileOrphanCleanupPolicyResponse
import me.moiclathy.edb.web.dto.DataFileOrphanCleanupResultResponse
import me.moiclathy.edb.web.dto.DataFileOrphanCleanupStateResponse
import me.moiclathy.edb.web.security.requireRole

fun Route.dataFileOrphanCleanupAdminRoutes(service: DataFileOrphanCleanupAdminService) {
    authenticate("auth-jwt") {
        route("/api/v1/admin/data-file-orphan-cleanup") {
            get {
                if (!requireDataFileCleanupAdmin()) return@get
                call.respond(
                    DataFileOrphanCleanupOverviewResponse(
                        policy = DataFileOrphanCleanupPolicyResponse.from(service.policy),
                        state = DataFileOrphanCleanupStateResponse.from(service.state())
                    )
                )
            }
            get("/estimate") {
                if (!requireDataFileCleanupAdmin()) return@get
                call.respond(DataFileOrphanCleanupEstimateResponse.from(service.estimate()))
            }
            post("/cleanup") {
                if (!requireDataFileCleanupAdmin()) return@post
                call.respond(DataFileOrphanCleanupResultResponse.from(service.cleanup()))
            }
            get("/last-result") {
                if (!requireDataFileCleanupAdmin()) return@get
                call.respond(DataFileOrphanCleanupStateResponse.from(service.state()))
            }
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.requireDataFileCleanupAdmin(): Boolean {
    val principal = call.principal<JWTPrincipal>()
        ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return false
        }
    return requireRole(principal, UserRole.ADMIN)
}
