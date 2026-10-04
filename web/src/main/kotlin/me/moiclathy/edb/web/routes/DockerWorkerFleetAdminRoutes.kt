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
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetApplicationService
import me.moiclathy.edb.core.worker.docker.DockerWorkerLogOptions
import me.moiclathy.edb.web.dto.DockerWorkerFleetResponse
import me.moiclathy.edb.web.dto.DockerWorkerLogsResponse
import me.moiclathy.edb.web.dto.DockerImageCatalogItemResponse
import me.moiclathy.edb.web.dto.DockerImageCatalogResponse
import me.moiclathy.edb.web.dto.DockerWorkerResponse
import me.moiclathy.edb.web.security.requireRole
import java.time.Duration

fun Route.dockerWorkerFleetAdminRoutes(service: DockerWorkerFleetApplicationService, config: AppConfig) {
    authenticate("auth-jwt") {
        route("/api/v1/admin/docker-workers") {
            get {
                if (!requireFleetAdmin()) return@get
                call.respond(DockerWorkerFleetResponse.from(service.state()))
            }

            get("/images") {
                if (!requireFleetAdmin()) return@get
                call.respond(DockerImageCatalogResponse(
                    buildList {
                        add(config.task.analysis.dockerWorker)
                        addAll(config.task.analysis.managedDockerWorkers)
                    }.distinctBy { it.id }.map(DockerImageCatalogItemResponse::from)
                ))
            }

            post("/start") {
                if (!requireFleetAdmin()) return@post
                call.respond(DockerWorkerFleetResponse.from(service.start()))
            }

            post("/stop") {
                if (!requireFleetAdmin()) return@post
                call.respond(DockerWorkerFleetResponse.from(service.stop()))
            }

            post("/{workerId}/restart") {
                if (!requireFleetAdmin()) return@post
                val workerId = requireWorkerId() ?: return@post
                call.respond(DockerWorkerResponse.from(workerId, service.restart(workerId)))
            }

            get("/{workerId}/logs") {
                if (!requireFleetAdmin()) return@get
                val workerId = requireWorkerId() ?: return@get
                val options = DockerWorkerLogOptions(
                    tailLines = call.request.queryParameters.int("tail", 200),
                    includeStdout = call.request.queryParameters.boolean("stdout", true),
                    includeStderr = call.request.queryParameters.boolean("stderr", true),
                    includeTimestamps = call.request.queryParameters.boolean("timestamps", true),
                    timeout = Duration.ofMillis(call.request.queryParameters.long("timeoutMs", 5_000))
                )
                call.respond(DockerWorkerLogsResponse.from(workerId, service.logs(workerId, options)))
            }
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.requireFleetAdmin(): Boolean {
    val principal = call.principal<JWTPrincipal>()
        ?: return false.also { call.respond(HttpStatusCode.Unauthorized) }
    return requireRole(principal, UserRole.ADMIN)
}

private suspend fun io.ktor.server.routing.RoutingContext.requireWorkerId(): String? {
    val workerId = call.parameters["workerId"]?.trim()
    if (workerId.isNullOrEmpty()) {
        call.respond(HttpStatusCode.BadRequest)
        return null
    }
    return workerId
}

private fun io.ktor.http.Parameters.int(name: String, default: Int): Int =
    this[name]?.toIntOrNull() ?: if (this[name] == null) default else throw IllegalArgumentException("$name must be an integer")

private fun io.ktor.http.Parameters.long(name: String, default: Long): Long =
    this[name]?.toLongOrNull() ?: if (this[name] == null) default else throw IllegalArgumentException("$name must be a long")

private fun io.ktor.http.Parameters.boolean(name: String, default: Boolean): Boolean = when (val value = this[name]) {
    null -> default
    "true" -> true
    "false" -> false
    else -> throw IllegalArgumentException("$name must be true or false")
}
