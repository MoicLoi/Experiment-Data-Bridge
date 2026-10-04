package me.moiclathy.edb.web.routes.project


import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import me.moiclathy.edb.core.service.ProjectConfigurationService
import me.moiclathy.edb.web.security.requireUserId
import me.moiclathy.edb.web.dto.GitRestoreRequest
import io.ktor.server.auth.jwt.JWTPrincipal
import me.moiclathy.edb.core.service.ProjectConfigurationPackageImportService
import io.ktor.http.content.*
import me.moiclathy.edb.core.git.GitCommitInfo
import org.koin.ktor.ext.inject


fun Route.projectGitRoutes(

    //configurationService: ProjectConfigurationService,

    //packageImportService: ProjectConfigurationPackageImportService

) {

    val configurationService: ProjectConfigurationService by inject()
    val packageImportService: ProjectConfigurationPackageImportService by inject()


    authenticate("auth-jwt") {


        route("/api/v1/projects/{id}/git") {

            get("/history") {


                val projectId =
                    call.parameters["id"]
                        ?.toIntOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest
                        )


                val userId =
                    call.requireUserId()
                        ?: return@get


                val result =
                    configurationService.history(
                        projectId,
                        userId
                    )


                call.respond(result)

            }

            get("/diff") {


                val projectId =
                    call.parameters["id"]
                        ?.toIntOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest
                        )


                val userId =
                    call.requireUserId()
                        ?: return@get


                val from =
                    call.request.queryParameters["from"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest
                        )


                val to =
                    call.request.queryParameters["to"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest
                        )


                val result =
                    configurationService.diff(
                        projectId,
                        userId,
                        from,
                        to
                    )


                call.respond(result)

            }


            post("/restore") {


                val projectId =
                    call.parameters["id"]
                        ?.toIntOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest
                        )


                val userId =
                    call.requireUserId()
                        ?: return@post


                val request =
                    call.receive<GitRestoreRequest>()


                val principal =
                    call.principal<JWTPrincipal>()


                val author =
                    principal
                        ?.payload
                        ?.subject
                        ?: "unknown"



                val result =
                    configurationService.restore(

                        projectId,

                        userId,

                        request.commitId,

                        author

                    )


                call.respond(result)

            }

            post("/package") {

                val projectId =
                    call.parameters["id"]
                        ?.toIntOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest
                        )

                val userId =
                    call.requireUserId()
                        ?: return@post

                val principal =
                    call.principal<JWTPrincipal>()

                val author =
                    principal
                        ?.payload
                        ?.subject
                        ?: "unknown"

                val multipart =
                    call.receiveMultipart()

                var result: GitCommitInfo? = null

                multipart.forEachPart { part ->

                    when (part) {

                        is PartData.FileItem -> {

                            part.streamProvider().use { input ->

                                result =
                                    packageImportService.importPackage(

                                        projectId = projectId,

                                        userId = userId,

                                        input = input,

                                        author = author

                                    )

                            }

                        }

                        else -> {}

                    }

                    part.dispose()

                }

                call.respond(

                    result
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing package file"
                        )

                )

            }


        }


    }

}