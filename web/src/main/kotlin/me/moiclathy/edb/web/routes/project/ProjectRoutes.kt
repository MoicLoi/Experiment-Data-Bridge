package me.moiclathy.edb.web.routes.project


import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.server.routing.*

import me.moiclathy.edb.core.database.model.CreateProjectCommand
import me.moiclathy.edb.core.project.ProjectMode
import me.moiclathy.edb.core.service.ProjectConfigurationService
import me.moiclathy.edb.core.service.ProjectService

import me.moiclathy.edb.web.dto.*
import me.moiclathy.edb.web.dto.toResponse
import me.moiclathy.edb.web.security.requireUserId
import org.koin.ktor.ext.inject


fun Route.projectRoutes(
    //projectService: ProjectService,
) {
    val projectService: ProjectService by inject()

    authenticate("auth-jwt") {

        route("/api/v1/projects") {
            get {
                val userId =
                    call.requireUserId()
                        ?: return@get

                val projects =
                    projectService.findByOwner(userId)
                call.respond(
                    projects.map {
                        it.toResponse()
                    }
                )
            }

            get("/{id}") {
                val id =
                    call.parameters["id"]
                        ?.toIntOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest
                        )

                val userId =
                    call.requireUserId()
                        ?: return@get

                val project =
                    projectService.getProject(
                        id,
                        userId
                    )


                call.respond(
                    project.toResponse()
                )
            }

            /*get("/{id}/diff") {
                // 1. 获取并校验 projectId
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                // 2. 获取当前登录用户的 userId
                val userId = call.requireUserId() ?: return@get

                // 3. 获取查询参数 from 和 to (即 oldCommit 和 newCommit)
                val from = call.request.queryParameters["from"]
                val to = call.request.queryParameters["to"]

                if (from.isNullOrBlank() || to.isNullOrBlank()) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        "Missing 'from' or 'to' commit parameters"
                    )
                }

                // 4. 调用服务层执行鉴权并计算 Diff
                val result = projectConfigurationService.diff(
                    projectId = id,
                    userId = userId,
                    oldCommit = from,
                    newCommit = to
                )

                // 5. 响应结果
                call.respond(result)
            }*/

            post {
                val userId =
                    call.requireUserId()
                        ?: return@post

                val request =
                    call.receive<CreateProjectRequest>()

                val command =
                    CreateProjectCommand(
                        name = request.name,
                        description = request.description,
                        ownerId = userId,
                        mode = ProjectMode.valueOf(request.mode),
                        configYaml = request.configYaml
                    )

                val id =
                    projectService.create(command)

                val project =
                    projectService.getProject(
                        id,
                        userId
                    )


                call.respond(
                    HttpStatusCode.Created,
                    project.toResponse()
                )
            }

            delete("/{id}") {


                val id =
                    call.parameters["id"]
                        ?.toIntOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest
                        )


                val userId =
                    call.requireUserId()
                        ?: return@delete



                projectService.deleteProject(
                    id,
                    userId
                )


                call.respond(
                    HttpStatusCode.NoContent
                )

            }

            put("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                val userId = call.requireUserId() ?: return@put
                val request = call.receive<UpdateProjectRequest>()

                val updated =
                    projectService.updateProject(
                        id,
                        userId,
                        CreateProjectCommand(
                            name = request.name,
                            description = request.description,
                            ownerId = userId,
                            mode = ProjectMode.valueOf(request.mode),
                            configYaml = request.configYaml
                        )
                    )


                call.respond(
                    updated.toResponse()
                )
            }

            post("/{id}/reload") {

                val projectId =
                    call.parameters["id"]
                        ?.toIntOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest
                        )

                val userId =
                    call.requireUserId()
                        ?: return@post

                projectService.getProject(
                    projectId,
                    userId
                )



                call.respond(HttpStatusCode.OK)
            }
        }
    }
}