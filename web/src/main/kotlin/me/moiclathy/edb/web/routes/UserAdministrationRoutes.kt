package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.database.model.User
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.service.UserAdministrationService
import me.moiclathy.edb.web.security.requireRole
import me.moiclathy.edb.web.security.userId
import me.moiclathy.edb.web.security.username

@Serializable
data class ChangeUserRoleRequest(val role: UserRole)

@Serializable
data class AdminUserResponse(
    val id: Int,
    val username: String,
    val fullName: String,
    val email: String?,
    val role: UserRole,
    val active: Boolean
) {
    companion object {
        fun from(user: User) = AdminUserResponse(
            id = user.id,
            username = user.username,
            fullName = user.fullName,
            email = user.email,
            role = user.role,
            active = user.isActive
        )
    }
}

fun Route.userAdministrationRoutes(service: UserAdministrationService) {
    authenticate("auth-jwt") {
        route("/api/v1/admin/users/{username}") {
            put("/role") {
                val principal = call.principal<JWTPrincipal>()!!
                if (!requireRole(principal, UserRole.ADMIN)) return@put
                val targetUsername = call.parameters["username"]
                    ?.takeIf { it.isNotBlank() }
                    ?: return@put call.respond(HttpStatusCode.BadRequest)
                val request = call.receive<ChangeUserRoleRequest>()
                val result = service.changeRole(
                    actorUserId = principal.userId(),
                    actorUsername = principal.username(),
                    targetUsername = targetUsername,
                    newRole = request.role
                )
                call.respond(HttpStatusCode.OK, AdminUserResponse.from(result))
            }
        }
    }
}
