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
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionAdminService
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.web.dto.DataFileRetentionEstimateResponse
import me.moiclathy.edb.web.dto.DataFileRetentionOverviewResponse
import me.moiclathy.edb.web.dto.DataFileRetentionPolicyResponse
import me.moiclathy.edb.web.dto.DataFileRetentionResultResponse
import me.moiclathy.edb.web.dto.DataFileRetentionStateResponse
import me.moiclathy.edb.web.security.requireRole

fun Route.dataFileRetentionAdminRoutes(service: DataFileRetentionAdminService) {
    authenticate("auth-jwt") {
        route("/api/v1/admin/data-file-retention") {
            get {
                if (!requireDataFileRetentionAdmin()) return@get
                call.respond(
                    DataFileRetentionOverviewResponse(
                        DataFileRetentionPolicyResponse.from(service.policy),
                        DataFileRetentionStateResponse.from(service.state())
                    )
                )
            }
            get("/estimate") {
                if (!requireDataFileRetentionAdmin()) return@get
                call.respond(DataFileRetentionEstimateResponse.from(service.estimate()))
            }
            post("/cleanup") {
                if (!requireDataFileRetentionAdmin()) return@post
                call.respond(DataFileRetentionResultResponse.from(service.cleanup()))
            }
            get("/last-result") {
                if (!requireDataFileRetentionAdmin()) return@get
                call.respond(DataFileRetentionStateResponse.from(service.state()))
            }
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.requireDataFileRetentionAdmin(): Boolean {
    val principal = call.principal<JWTPrincipal>()
        ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return false
        }
    return requireRole(principal, UserRole.ADMIN)
}
