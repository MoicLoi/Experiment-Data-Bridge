package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import me.moiclathy.edb.core.audit.model.AuditLogQuery
import me.moiclathy.edb.core.service.AuditService
import me.moiclathy.edb.web.dto.AuditLogListResponse
import me.moiclathy.edb.web.dto.AuditLogResponse
import org.koin.ktor.ext.inject

fun Route.auditRoutes() {

    val auditService by inject<AuditService>()

    authenticate("auth-jwt") {

        route("/api/v1/audit") {

            get {

                val result =
                    auditService.search(
                        AuditLogQuery()
                    )

                call.respond(
                    HttpStatusCode.OK,
                    AuditLogListResponse.from(result)
                )
            }

            get("/{id}") {

                val id =
                    call.parameters["id"]
                        ?.toLongOrNull()

                if (id == null || id <= 0) {

                    call.respond(
                        HttpStatusCode.BadRequest
                    )

                    return@get
                }

                val result =
                    auditService.get(id)

                if (result == null) {

                    call.respond(
                        HttpStatusCode.NotFound
                    )

                    return@get
                }

                call.respond(
                    HttpStatusCode.OK,
                    AuditLogResponse.from(result)
                )
            }
        }
    }
}