package me.moiclathy.edb.cli.service

import me.moiclathy.edb.core.plugin.DirectoryPluginLoader
import me.moiclathy.edb.core.plugin.PluginLoadFailureCode
import java.nio.file.Path

data class PluginCliSummary(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: String,
    val jar: Path,
    val taskTypes: List<String>,
    val workflowStepTypes: List<String>
)

data class PluginCliFailure(
    val jar: Path,
    val code: PluginLoadFailureCode,
    val message: String
)

data class PluginCliScan(
    val plugins: List<PluginCliSummary>,
    val failures: List<PluginCliFailure>
)

class PluginCliService(
    private val loader: DirectoryPluginLoader = DirectoryPluginLoader()
) {
    fun list(directory: Path): PluginCliScan = loader.load(directory).use { result ->
        PluginCliScan(
            plugins = result.loaded.map { loaded ->
                PluginCliSummary(
                    id = loaded.descriptor.id,
                    name = loaded.descriptor.name,
                    version = loaded.descriptor.version,
                    apiVersion = loaded.descriptor.apiVersion,
                    jar = loaded.jar,
                    taskTypes = loaded.contributions.taskHandlers.map { it.taskType }.sorted(),
                    workflowStepTypes = loaded.contributions.workflowStepHandlers.map { it.type }.sorted()
                )
            },
            failures = result.failures.map { PluginCliFailure(it.jar, it.code, it.message) }
        )
    }

    fun inspect(directory: Path, pluginId: String): PluginCliSummary? =
        list(directory).plugins.firstOrNull { it.id == pluginId }

    fun validate(jar: Path): PluginCliScan = loader.loadJar(jar).use { result ->
        PluginCliScan(
            plugins = result.loaded.map { loaded ->
                PluginCliSummary(
                    id = loaded.descriptor.id,
                    name = loaded.descriptor.name,
                    version = loaded.descriptor.version,
                    apiVersion = loaded.descriptor.apiVersion,
                    jar = loaded.jar,
                    taskTypes = loaded.contributions.taskHandlers.map { it.taskType }.sorted(),
                    workflowStepTypes = loaded.contributions.workflowStepHandlers.map { it.type }.sorted()
                )
            },
            failures = result.failures.map { PluginCliFailure(it.jar, it.code, it.message) }
        )
    }
}
