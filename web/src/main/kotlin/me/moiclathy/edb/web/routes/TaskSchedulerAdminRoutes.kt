package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.application.TaskSchedulerApplicationService
import me.moiclathy.edb.web.dto.TaskSchedulerStateResponse
import me.moiclathy.edb.web.dto.UpdateTaskSchedulerWorkersRequest
import me.moiclathy.edb.web.security.requireRole

fun Route.taskSchedulerAdminRoutes(service: TaskSchedulerApplicationService) {
    authenticate("auth-jwt") {
        route("/api/v1/admin/task-scheduler") {
            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                if (!requireRole(principal, UserRole.ADMIN)) return@get
                call.respond(TaskSchedulerStateResponse.from(service.state()))
            }

            post("/start") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                if (!requireRole(principal, UserRole.ADMIN)) return@post
                call.respond(TaskSchedulerStateResponse.from(service.start()))
            }

            post("/stop") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                if (!requireRole(principal, UserRole.ADMIN)) return@post
                call.respond(TaskSchedulerStateResponse.from(service.stop()))
            }

            put("/workers") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized)
                if (!requireRole(principal, UserRole.ADMIN)) return@put
                val request = call.receive<UpdateTaskSchedulerWorkersRequest>()
                call.respond(
                    TaskSchedulerStateResponse.from(
                        service.updateWorkerCount(request.workerCount)
                    )
                )
            }
        }
    }
}
