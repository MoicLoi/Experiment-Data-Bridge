package me.moiclathy.edb.core.plugin

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.execution.TaskHandlerRegistry
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginRegistryAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `adapts plugin task and workflow handlers into core registries`() = runBlocking {
        val tasks = TaskHandlerRegistry()
        val workflows = WorkflowStepHandlerRegistry()
        val loaded = loadedPlugin(ValidPlugin())
        val registration = PluginRegistryAdapter(tasks, workflows).register(listOf(loaded))

        assertEquals(setOf("example.valid"), registration.pluginIds)
        val taskResult = tasks.find("example.task")!!.execute(
            Task.pending(
                projectId = 7,
                taskType = "example.task",
                configSnapshot = "{}",
                triggerUserId = 3,
                createdAt = Instant.parse("2026-08-11T00:00:00Z")
            ),
            RecordingTaskContext()
        )
        assertEquals("7", taskResult!!.metadata["projectId"])

        val workflowResult = workflows.require("example.step").execute(
            WorkflowStepDefinition("step-1", "example.step", mapOf("value" to "done")),
            PipelineContext(mapOf("input" to 42))
        ) as PipelineStepResult.Success
        assertEquals("done", workflowResult.context["plugin.output"])
        loaded.close()
    }

    @Test
    fun `rejects contribution that conflicts with an existing registry handler`() {
        val conflictingTasks = TaskHandlerRegistry(listOf(ExistingTaskHandler()))
        val loaded = loadedPlugin(ValidPlugin())

        assertFailsWith<PluginRegistrationException> {
            PluginRegistryAdapter(conflictingTasks, WorkflowStepHandlerRegistry()).register(listOf(loaded))
        }
        loaded.close()
    }

    private fun loadedPlugin(plugin: ValidPlugin): LoadedPlugin = LoadedPlugin(
        jar = directory.resolve("fixture.jar"),
        plugin = plugin,
        descriptor = plugin.descriptor,
        contributions = plugin.contributions(),
        classLoader = URLClassLoader(emptyArray(), plugin::class.java.classLoader)
    )

    private class RecordingTaskContext : TaskExecutionContext {
        override suspend fun ensureNotCancelled() = Unit

        override suspend fun reportProgress(percent: Int, message: String?): TaskProgress =
            TaskProgress(percent, message, Instant.parse("2026-08-11T00:00:00Z"))
    }

    private class ExistingTaskHandler : me.moiclathy.edb.core.task.execution.TaskHandler {
        override val taskType = "example.task"
        override suspend fun execute(task: Task, context: TaskExecutionContext) = null
    }
}
