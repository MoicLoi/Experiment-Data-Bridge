package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.moiclathy.edb.core.task.application.TaskApplicationService
import me.moiclathy.edb.core.task.application.TaskListQuery
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.web.dto.SubmitPipelineTaskRequest
import me.moiclathy.edb.web.dto.TaskAuditEventResponse
import me.moiclathy.edb.web.dto.TaskPageResponse
import me.moiclathy.edb.web.dto.TaskResponse
import me.moiclathy.edb.web.dto.TaskTimelineResponse
import me.moiclathy.edb.web.security.requireUserId

fun Route.taskRoutes(service: TaskApplicationService) {
    authenticate("auth-jwt") {
        route("/api/v1/tasks") {
            post("/pipeline") {
                val userId = call.requireUserId() ?: return@post
                val request = call.receive<SubmitPipelineTaskRequest>()
                val task = service.submitPipelineTask(request.toCommand(userId))
                call.respond(HttpStatusCode.Created, TaskResponse.from(task))
            }

            get("/{taskId}") {
                val userId = call.requireUserId() ?: return@get
                val taskId = call.parameters["taskId"]?.toIntOrNull()
                    ?: throw IllegalArgumentException("Task ID must be a valid integer.")
                call.respond(TaskResponse.from(service.getTask(taskId, userId)))
            }

            post("/{taskId}/cancel") {
                val userId = call.requireUserId() ?: return@post
                val taskId = call.parameters["taskId"]?.toIntOrNull()
                    ?: throw IllegalArgumentException("Task ID must be a valid integer.")
                val task = service.requestCancellation(taskId, userId)
                call.respond(TaskResponse.from(task))
            }

            get("/{taskId}/timeline") {
                val userId = call.requireUserId() ?: return@get
                val taskId = call.parameters["taskId"]?.toIntOrNull()
                    ?: throw IllegalArgumentException("Task ID must be a valid integer.")
                val events = service.getTaskTimeline(taskId, userId)
                call.respond(
                    TaskTimelineResponse(
                        taskId = taskId,
                        events = events.map(TaskAuditEventResponse::from)
                    )
                )
            }
        }

        get("/api/v1/projects/{projectId}/tasks") {
            val userId = call.requireUserId() ?: return@get
            val projectId = call.parameters["projectId"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Project ID must be a valid integer.")
            val status = call.request.queryParameters["status"]
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    runCatching { TaskStatus.valueOf(it.uppercase()) }
                        .getOrElse { throw IllegalArgumentException("Unknown task status: $it") }
                }
            val page = call.request.queryParameters.intValue("page", 0)
            val size = call.request.queryParameters.intValue("size", 20)
            val result = service.listTasks(
                TaskListQuery(projectId, status, page, size, userId)
            )
            call.respond(TaskPageResponse.from(result))
        }
    }
}

private fun io.ktor.http.Parameters.intValue(name: String, default: Int): Int {
    val value = this[name] ?: return default
    return value.toIntOrNull()
        ?: throw IllegalArgumentException("Query parameter $name must be a valid integer.")
}
