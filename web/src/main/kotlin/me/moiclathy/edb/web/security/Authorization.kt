package me.moiclathy.edb.web.security

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import me.moiclathy.edb.core.security.UserRole

suspend fun RoutingContext.requireRole(
    principal: JWTPrincipal,
    role: UserRole
): Boolean {

    if (principal.role() != role) {

        call.respond(
            HttpStatusCode.Forbidden,
            mapOf(
                "message" to "Permission denied"
            )
        )

        return false
    }

    return true
}