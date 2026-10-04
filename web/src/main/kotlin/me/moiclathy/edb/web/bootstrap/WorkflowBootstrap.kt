package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.task.workflow.DirectoryWorkflowLoader
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.nio.file.Path

/** Loads configured JSON workflows before the task scheduler starts. */
fun Application.bootstrapWorkflows() {
    val logger = LoggerFactory.getLogger("WorkflowBootstrap")
    val config by inject<AppConfig>()
    val workflow = config.task.workflow
    if (!workflow.enabled) {
        logger.info("JSON Workflow loading is disabled.")
        return
    }

    val loader by inject<DirectoryWorkflowLoader>()
    val result = loader.load(Path.of(workflow.directory))
    logger.info(
        "Loaded {} JSON workflows from {}: {}",
        result.pipelineIds.size,
        result.directory,
        result.pipelineIds.joinToString()
    )
}
