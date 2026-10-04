package me.moiclathy.edb.web

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import me.moiclathy.edb.web.serialization.kotlinxJsonJacksonModule
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.config.loadConfig
import me.moiclathy.edb.core.security.JwtService
import me.moiclathy.edb.core.database.repository.UserRepository
import me.moiclathy.edb.web.security.JwtAccountValidator
import me.moiclathy.edb.web.bootstrap.bootstrap
import me.moiclathy.edb.web.bootstrap.configureTaskSchedulerLifecycle
import me.moiclathy.edb.web.bootstrap.configureDockerWorkerFleetLifecycle
import me.moiclathy.edb.web.bootstrap.configureTaskEventBusApiLifecycle
import me.moiclathy.edb.web.bootstrap.configureTaskRetentionLifecycle
import me.moiclathy.edb.web.bootstrap.configureAgentRetentionLifecycle
import me.moiclathy.edb.web.bootstrap.configureDataFileOrphanCleanupLifecycle
import me.moiclathy.edb.web.bootstrap.configureDataFileRetentionLifecycle
import me.moiclathy.edb.web.bootstrap.bootstrapWorkflows
import me.moiclathy.edb.web.bootstrap.bootstrapWorkflowTemplates
import me.moiclathy.edb.web.bootstrap.bootstrapReportDefinitions
import me.moiclathy.edb.web.bootstrap.bootstrapPlugins
import me.moiclathy.edb.web.di.edbModules
import me.moiclathy.edb.web.plugin.configureExceptionHandling
import me.moiclathy.edb.web.plugin.configureRequestId
import me.moiclathy.edb.web.routes.adminRoutes
import me.moiclathy.edb.web.routes.authRoutes
import me.moiclathy.edb.web.routes.config.configurationCompareRoutes
import me.moiclathy.edb.web.routes.config.configurationSnapshotRoutes
import me.moiclathy.edb.web.routes.project.*
import me.moiclathy.edb.web.routes.template.templateRoutes
import me.moiclathy.edb.web.routes.userRoutes
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.slf4j.LoggerFactory
import java.io.File
import me.moiclathy.edb.web.routes.auditRoutes
import me.moiclathy.edb.web.routes.config.configurationMetadataRoutes
import me.moiclathy.edb.web.routes.config.configurationMetadataTagRoutes
import me.moiclathy.edb.web.routes.taskRoutes
import me.moiclathy.edb.web.routes.taskEventStreamRoutes
import me.moiclathy.edb.web.routes.taskSchedulerAdminRoutes
import me.moiclathy.edb.web.routes.taskMetricsAdminRoutes
import me.moiclathy.edb.web.routes.taskRetryAdminRoutes
import me.moiclathy.edb.web.routes.taskRetentionAdminRoutes
import me.moiclathy.edb.web.routes.taskApiDocumentationRoutes
import me.moiclathy.edb.web.routes.dataFileRoutes
import me.moiclathy.edb.web.routes.dataFileConversionRoutes
import me.moiclathy.edb.web.routes.reportRoutes
import me.moiclathy.edb.web.routes.dataFileApiDocumentationRoutes
import me.moiclathy.edb.web.routes.dataFileOrphanCleanupAdminRoutes
import me.moiclathy.edb.web.routes.dataFileRetentionAdminRoutes
import me.moiclathy.edb.web.routes.analysisRoutes
import me.moiclathy.edb.web.routes.userAdministrationRoutes
import me.moiclathy.edb.web.routes.dockerWorkerFleetAdminRoutes
import me.moiclathy.edb.web.routes.dockerRegistryProfileAdminRoutes
import me.moiclathy.edb.web.routes.dockerWorkerRebuildAdminRoutes
import me.moiclathy.edb.web.routes.workflowStepCatalogRoutes
import me.moiclathy.edb.web.routes.workflowIrRoutes
import me.moiclathy.edb.web.routes.workflowTemplateRoutes
import me.moiclathy.edb.web.routes.phraseDefinitionRoutes
import me.moiclathy.edb.web.routes.agentRoutes
import me.moiclathy.edb.web.routes.agentModelProfileRoutes
import me.moiclathy.edb.web.routes.agentProviderAdminRoutes
import me.moiclathy.edb.web.routes.agentOperationsAdminRoutes
import me.moiclathy.edb.web.routes.agentEventStreamRoutes
import me.moiclathy.edb.web.routes.agentApiDocumentationRoutes
import me.moiclathy.edb.web.routes.frontendDebugRoutes

fun main() {
    val logger = LoggerFactory.getLogger("Application")
    logger.info("Starting EDB server...")

    val config = loadConfig()

    embeddedServer(
        Netty,
        port = config.server.port,
        host = config.server.host,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    // 1. 启动 Koin 插件
    install(Koin) {
        modules(edbModules)
    }

    val logger = LoggerFactory.getLogger("Application")
    val config = get<AppConfig>()
    val jwtService by inject<JwtService>()
    val userRepository by inject<UserRepository>()
    val jwtAccountValidator = JwtAccountValidator(userRepository)

    logger.info(
        "EDB server listening on {}:{}",
        config.server.host,
        config.server.port
    )

    // 2. 领域引导初始化（数据库与管理员）
    bootstrap()
    bootstrapPlugins()
    bootstrapWorkflows()
    bootstrapWorkflowTemplates()
    bootstrapReportDefinitions()
    configureTaskEventBusApiLifecycle()
    configureTaskRetentionLifecycle()
    configureAgentRetentionLifecycle()
    configureDataFileOrphanCleanupLifecycle()
    configureDataFileRetentionLifecycle()
    configureDockerWorkerFleetLifecycle()
    configureTaskSchedulerLifecycle()

    // 3. 基础插件与安全配置
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            registerModule(kotlinxJsonJacksonModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    configureRequestId()
    configureExceptionHandling()

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(jwtService.verifier)
            validate { credential ->
                if (jwtAccountValidator.isCurrent(
                        credential.payload.subject,
                        credential.payload.getClaim("userId").asInt(),
                        credential.payload.getClaim("role").asString()
                    )) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }

    // 4. 路由注册
    routing {
        get("/api/v1/health") {
            call.respond(mapOf("status" to "UP", "version" to "0.25.0-demo.1"))
        }
        frontendDebugRoutes(get())
        taskApiDocumentationRoutes()
        dataFileApiDocumentationRoutes()
        agentApiDocumentationRoutes()
        authRoutes(get())
        userRoutes()
        adminRoutes()
        userAdministrationRoutes(get())
        auditRoutes()

        projectRoutes()
        projectGitRoutes()
        projectConfigurationRoutes()
        projectPackageRoute(get())
        templateRoutes(get())
        projectConfigurationMergeRoutes(get(), get(), get())
        configurationCompareRoutes(get(), get(), get())
        configurationSnapshotRoutes(get(), get())
        configurationMetadataRoutes(get(), get())
        configurationMetadataTagRoutes(get(), get())
        dataFileRoutes(get(), get())
        dataFileConversionRoutes(get())
        reportRoutes(get())
        analysisRoutes(get(), get())
        taskRoutes(get())
        taskEventStreamRoutes(get(), get())
        taskSchedulerAdminRoutes(get())
        taskMetricsAdminRoutes(get())
        taskRetryAdminRoutes(get())
        taskRetentionAdminRoutes(get())
        dataFileOrphanCleanupAdminRoutes(get())
        dataFileRetentionAdminRoutes(get())
        dockerWorkerFleetAdminRoutes(get(), get())
        dockerRegistryProfileAdminRoutes(get())
        dockerWorkerRebuildAdminRoutes(get())
        workflowStepCatalogRoutes(get())
        workflowIrRoutes(get())
        workflowTemplateRoutes(get(), get())
        phraseDefinitionRoutes(get(), get())
        agentRoutes(get(), get(), get(), get(), get(), get())
        agentModelProfileRoutes(get())
        agentProviderAdminRoutes(get())
        agentOperationsAdminRoutes(get(), get())
        agentEventStreamRoutes(get(), get())

        projectConfigurationExportRoutes(get())
        projectConfigurationImportRoutes(get())

        authenticate("auth-jwt") {
            get("/api/v1/user/me") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.payload?.subject ?: "unknown"
                val role = principal?.payload?.getClaim("role")?.asString() ?: "USER"

                call.respond(
                    mapOf(
                        "username" to username,
                        "role" to role
                    )
                )
            }
        }

        val webRoot = File("web")
        if (webRoot.isDirectory) {
            val assetRoot = File(webRoot, "assets")
            if (assetRoot.isDirectory) {
                staticFiles("/assets", assetRoot)
            }
            get("/") {
                call.respondFile(File(webRoot, "index.html"))
            }
            get("/{...}") {
                call.respondFile(File(webRoot, "index.html"))
            }
        }
    }
}
