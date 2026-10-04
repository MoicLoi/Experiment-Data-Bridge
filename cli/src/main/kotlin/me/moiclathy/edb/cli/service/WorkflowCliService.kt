package me.moiclathy.edb.cli.service

import me.moiclathy.edb.core.plugin.DirectoryPluginLoader
import me.moiclathy.edb.core.plugin.PluginRegistryAdapter
import me.moiclathy.edb.core.task.execution.TaskHandlerRegistry
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.workflow.CopyContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.DirectoryWorkflowLoader
import me.moiclathy.edb.core.task.workflow.JsonWorkflowDriver
import me.moiclathy.edb.core.task.workflow.KotlinxJsonWorkflowParser
import me.moiclathy.edb.core.task.workflow.SetContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowCompiler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class WorkflowCliSummary(
    val id: String,
    val file: Path
)

class WorkflowCliException(message: String) : IllegalArgumentException(message)

class WorkflowCliService(
    private val pluginLoader: DirectoryPluginLoader = DirectoryPluginLoader()
) {
    fun list(workflowDirectory: Path, pluginDirectory: Path): List<WorkflowCliSummary> =
        withRuntime(pluginDirectory) { driver, registry ->
            val result = DirectoryWorkflowLoader(driver, registry).load(workflowDirectory)
            result.pipelineIds.zip(result.files).map { (id, file) -> WorkflowCliSummary(id, file) }
        }

    fun validate(file: Path, pluginDirectory: Path): WorkflowCliSummary =
        withRuntime(pluginDirectory) { driver, _ ->
            require(Files.isRegularFile(file)) { "Workflow file does not exist: ${file.toAbsolutePath()}" }
            val pipeline = driver.compile(Files.readString(file, StandardCharsets.UTF_8).removePrefix("\uFEFF"))
            WorkflowCliSummary(pipeline.id, file.toAbsolutePath().normalize())
        }

    private fun <T> withRuntime(
        pluginDirectory: Path,
        block: (JsonWorkflowDriver, PipelineRegistry) -> T
    ): T = pluginLoader.load(pluginDirectory).use { plugins ->
        if (plugins.failures.isNotEmpty()) {
            val first = plugins.failures.first()
            throw WorkflowCliException("Plugin ${first.jar.fileName} failed [${first.code}]: ${first.message}")
        }
        val workflowHandlers = WorkflowStepHandlerRegistry(
            listOf(SetContextWorkflowStepHandler(), CopyContextWorkflowStepHandler())
        )
        PluginRegistryAdapter(TaskHandlerRegistry(), workflowHandlers).register(plugins.loaded)
        val registry = PipelineRegistry()
        val driver = JsonWorkflowDriver(KotlinxJsonWorkflowParser(), WorkflowCompiler(workflowHandlers))
        block(driver, registry)
    }
}
