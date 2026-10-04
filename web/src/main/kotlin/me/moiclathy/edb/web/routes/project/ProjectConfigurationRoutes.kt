package me.moiclathy.edb.web.routes.project

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.moiclathy.edb.core.service.ProjectConfigurationExportService
import me.moiclathy.edb.core.service.ProjectConfigurationService
import me.moiclathy.edb.web.security.requireUserId
import me.moiclathy.edb.core.config.export.ConfigurationExportResult
import me.moiclathy.edb.core.service.ProjectConfigurationValidationService
import me.moiclathy.edb.web.dto.ValidateConfigurationRequest
import me.moiclathy.edb.web.dto.toResponse
import org.koin.ktor.ext.inject


data class UpdateConfigurationRequest(
    val yaml:String
)


fun Route.projectConfigurationRoutes(
    //configurationService: ProjectConfigurationService,
    //validationService: ProjectConfigurationValidationService,
    //exportService: ProjectConfigurationExportService
){

    val configurationService: ProjectConfigurationService by inject()
    val validationService: ProjectConfigurationValidationService by inject()
    val exportService: ProjectConfigurationExportService by inject()

    authenticate("auth-jwt") {


        route("/api/v1/projects/{id}/configuration") {


            put {

                val projectId =
                    call.parameters["id"]
                        ?.toIntOrNull()
                        ?: return@put call.respond(
                            HttpStatusCode.BadRequest
                        )


                val userId =
                    call.requireUserId()
                        ?: return@put


                val request =
                    call.receive<UpdateConfigurationRequest>()


                val principal =
                    call.principal<JWTPrincipal>()


                val author =
                    principal
                        ?.payload
                        ?.subject
                        ?: "unknown"


                val result =
                    configurationService.saveConfiguration(
                        projectId,
                        userId = userId,
                        request.yaml,
                        author,
                        messageBuilder = { "Update configuration via Web API by $author" }
                    )


                call.respond(result)

            }

            get("/export") {

                val projectId =
                    call.parameters["id"]
                        ?.toIntOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest
                        )

                val userId =
                    call.requireUserId()
                        ?: return@get

                val result: me.moiclathy.edb.core.config.export.ConfigurationExportResult =
                    exportService.export(projectId, userId)


                call.response.header(

                    HttpHeaders.ContentDisposition,

                    """attachment; filename="${result.fileName}""""

                )

                call.respondText(

                    text = result.yaml,

                    contentType =
                        ContentType.parse(
                            "application/x-yaml"
                        )

                )

            }

            get("/preview") {

                val projectId =
                    call.parameters["id"]
                        ?.toIntOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest
                        )

                val userId =
                    call.requireUserId()
                        ?: return@get

                // 获取可选的 commit 查询参数（为 null 时代表当前配置）
                val commit =
                    call.request.queryParameters["commit"]

                // 根据是否传入 commit，调用对应的业务逻辑（或复用支持重载的方法）
                val result = if (commit.isNullOrBlank()) {
                    // 当前配置预览（使用原有的 exportService 或 configurationService 预览逻辑）
                    exportService.preview(projectId, userId)
                } else {
                    // 历史版本配置预览
                    configurationService.preview(projectId, userId, commit)
                }

                call.respond(result)
            }

            post("/validate") {

                val request =
                    call.receive<ValidateConfigurationRequest>()

                val result =
                    validationService.validate(
                        request.yaml
                    )

                call.respond(result.toResponse())

            }

        }

    }

}