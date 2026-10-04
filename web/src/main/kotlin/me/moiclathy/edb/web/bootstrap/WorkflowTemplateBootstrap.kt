package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.task.workflow.template.DirectoryWorkflowTemplateLoader
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.nio.file.Path

fun Application.bootstrapWorkflowTemplates() {
    val logger = LoggerFactory.getLogger("WorkflowTemplateBootstrap")
    val config by inject<AppConfig>()
    val loader by inject<DirectoryWorkflowTemplateLoader>()
    val result = loader.load(Path.of(config.task.workflow.templateDirectory))
    logger.info("Loaded {} workflow templates from {}: {}", result.templateIds.size, result.directory, result.templateIds.joinToString())
}
