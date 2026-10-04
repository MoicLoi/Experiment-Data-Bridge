package me.moiclathy.edb.web.routes

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.moiclathy.edb.web.security.role
import me.moiclathy.edb.web.security.username
import kotlin.collections.mapOf

fun Route.userRoutes() {

    authenticate("auth-jwt") {

        get("/api/v1/user/me") {

            val principal = call.principal<JWTPrincipal>()!!

            call.respond(
                mapOf(
                    "username" to principal.username(),
                    "role" to principal.role().name
                )
            )
        }
    }
}