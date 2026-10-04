package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.datafile.report.DirectoryReportDefinitionLoader
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.nio.file.Path

fun Application.bootstrapReportDefinitions() {
    val logger = LoggerFactory.getLogger("ReportDefinitionBootstrap")
    val config by inject<AppConfig>()
    if (!config.task.report.enabled) {
        logger.info("Report definition loading is disabled.")
        return
    }
    val loader by inject<DirectoryReportDefinitionLoader>()
    val result = loader.load(Path.of(config.task.report.directory))
    logger.info(
        "Loaded {} report definitions from {}: {}",
        result.definitionIds.size,
        result.directory,
        result.definitionIds.joinToString()
    )
}
