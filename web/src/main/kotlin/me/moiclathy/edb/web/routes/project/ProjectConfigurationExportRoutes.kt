package me.moiclathy.edb.web.routes.project

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.moiclathy.edb.core.service.ProjectConfigurationExportService
import me.moiclathy.edb.web.security.requireUserId

// 引入你的 requireUserId 扩展函数所在的包

// 定义一个 Route 的扩展函数，不需要写 class
fun Route.projectConfigurationExportRoutes(exportService: ProjectConfigurationExportService) {
    authenticate("auth-jwt") {
        get("/api/v1/projects/{id}/configuration/export") {
            val projectId = call.parameters["id"]
                ?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val userId = call.requireUserId()
                ?: return@get

            val result =
                exportService.export(
                    projectId,
                    userId
                )

            // 浏览器下载文件名
            call.response.header(
                HttpHeaders.ContentDisposition,
                """attachment; filename="${result.fileName}""""
            )

            call.respondText(
                text = result.yaml,
                contentType = ContentType.parse("application/x-yaml")
            )
        }
    }
}