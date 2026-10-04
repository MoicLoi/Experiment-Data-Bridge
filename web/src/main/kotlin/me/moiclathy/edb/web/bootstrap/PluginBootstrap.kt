package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.plugin.DirectoryPluginLoader
import me.moiclathy.edb.core.plugin.PluginRegistryAdapter
import me.moiclathy.edb.core.plugin.declarative.DeclarativeHttpPluginLoader
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandlerRegistry
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory
import java.nio.file.Path

/** Loads immutable plugin JARs once, before workflows are compiled and workers start. */
fun Application.bootstrapPlugins(
    config: AppConfig = get(),
    loader: DirectoryPluginLoader = get(),
    registryAdapter: PluginRegistryAdapter = get(),
    declarativeLoader: DeclarativeHttpPluginLoader = get(),
    analysisHandlers: AnalysisHandlerRegistry = get()
) {
    val logger = LoggerFactory.getLogger("PluginBootstrap")
    val pluginConfig = config.plugins
    if (!pluginConfig.enabled) {
        logger.info("Plugin loading is disabled.")
        return
    }

    val result = loader.load(Path.of(pluginConfig.directory))
    if (result.failures.isNotEmpty()) {
        result.failures.forEach { failure ->
            logger.error(
                "Plugin JAR {} failed [{}]: {}",
                failure.jar,
                failure.code,
                failure.message,
                failure.cause
            )
        }
        if (pluginConfig.failOnError) {
            result.close()
            error("Plugin loading failed for ${result.failures.size} JAR(s).")
        }
    }

    try {
        val registration = registryAdapter.register(result.loaded)
        logger.info(
            "Loaded {} plugin(s) from {} with {} task type(s) and {} workflow step type(s): {}",
            registration.pluginIds.size,
            result.directory,
            registration.taskTypes.size,
            registration.workflowStepTypes.size,
            registration.pluginIds.joinToString()
        )

        val declarative = declarativeLoader.load(Path.of(pluginConfig.declarativeDirectory))
        declarative.failures.forEach { failure ->
            logger.error("Declarative plugin {} failed: {}", failure.descriptor, failure.message, failure.cause)
        }
        if (pluginConfig.failOnError && declarative.failures.isNotEmpty()) {
            error("Declarative plugin loading failed for ${declarative.failures.size} descriptor(s).")
        }
        declarative.handlers.forEach(analysisHandlers::register)
        logger.info(
            "Loaded {} declarative HTTP plugin(s) with {} analysis capability/capabilities from {}.",
            declarative.loaded.size,
            declarative.handlers.size,
            declarative.directory
        )
    } catch (failure: Throwable) {
        result.close()
        throw failure
    }

    monitor.subscribe(ApplicationStopping) {
        logger.info("Closing {} loaded plugin(s).", result.loaded.size)
        result.close()
    }
}
