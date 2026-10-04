package me.moiclathy.edb.web.routes.template

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.template.model.Template
import me.moiclathy.edb.core.template.model.TemplateMetadata
import me.moiclathy.edb.core.template.model.TemplateQuery
import me.moiclathy.edb.core.template.model.TemplateType
import me.moiclathy.edb.core.template.model.TemplateVisibility
import me.moiclathy.edb.core.template.service.TemplateService
import me.moiclathy.edb.web.dto.CreateTemplateRequest
import me.moiclathy.edb.web.dto.TemplateResponse
import me.moiclathy.edb.web.dto.UpdateTemplateRequest
import me.moiclathy.edb.web.security.role
import me.moiclathy.edb.web.security.username
import java.time.Instant

fun Route.templateRoutes(service: TemplateService) {
    authenticate("auth-jwt") {
        route("/api/v1/templates") {
            post {
                val principal = call.principal<JWTPrincipal>()!!
                val request = call.receive<CreateTemplateRequest>()
                val now = Instant.now()
                val result = service.create(Template(
                    id = null,
                    name = request.name,
                    description = request.description,
                    type = request.type,
                    visibility = request.visibility,
                    metadata = TemplateMetadata(now, now, principal.username(), 1),
                    yaml = request.yaml
                ))
                call.respond(HttpStatusCode.Created, TemplateResponse(result))
            }

            get("/count") {
                val principal = call.principal<JWTPrincipal>()!!
                val visibleCount = service.search(TemplateQuery()).count { summary ->
                    canRead(service.load(summary.id), principal)
                }
                call.respond(mapOf("count" to visibleCount))
            }

            get("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val principal = call.principal<JWTPrincipal>()!!
                val template = service.load(id)
                if (!canRead(template, principal)) return@get call.respond(HttpStatusCode.Forbidden)
                call.respond(TemplateResponse(template))
            }

            put("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@put call.respond(HttpStatusCode.BadRequest)
                val principal = call.principal<JWTPrincipal>()!!
                val old = service.load(id)
                if (!canWrite(old, principal)) return@put call.respond(HttpStatusCode.Forbidden)
                val request = call.receive<UpdateTemplateRequest>()
                val updated = old.copy(
                    name = request.name,
                    description = request.description,
                    type = request.type,
                    visibility = request.visibility,
                    yaml = request.yaml,
                    metadata = old.metadata.copy(updatedAt = Instant.now(), version = old.metadata.version + 1)
                )
                call.respond(TemplateResponse(service.update(updated)))
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val principal = call.principal<JWTPrincipal>()!!
                val template = service.load(id)
                if (!canWrite(template, principal)) return@delete call.respond(HttpStatusCode.Forbidden)
                service.delete(id)
                call.respond(HttpStatusCode.NoContent)
            }

            get {
                val principal = call.principal<JWTPrincipal>()!!
                val type = call.request.queryParameters["type"]?.let { TemplateType.valueOf(it.uppercase()) }
                val visibility = call.request.queryParameters["visibility"]?.let { TemplateVisibility.valueOf(it.uppercase()) }
                val result = service.search(TemplateQuery(
                    call.request.queryParameters["keyword"], type, visibility
                )).filter { summary -> canRead(service.load(summary.id), principal) }
                call.respond(result)
            }
        }
    }
}

private fun canRead(template: Template, principal: JWTPrincipal): Boolean =
    template.visibility != TemplateVisibility.PRIVATE || canWrite(template, principal)

private fun canWrite(template: Template, principal: JWTPrincipal): Boolean =
    principal.role() == UserRole.ADMIN || template.metadata.author == principal.username()
