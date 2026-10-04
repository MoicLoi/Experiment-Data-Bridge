package me.moiclathy.edb.web.routes

import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.web.security.requireRole

fun Route.adminRoutes() {

    authenticate("auth-jwt") {

        get("/api/v1/admin/test") {

            val principal =
                call.principal<JWTPrincipal>()!!

            if (!requireRole(principal, UserRole.ADMIN))
                return@get

            call.respond(

                mapOf(

                    "message" to "welcome admin"

                )

            )
        }

    }

}