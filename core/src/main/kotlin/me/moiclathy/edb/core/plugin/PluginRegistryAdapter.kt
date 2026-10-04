package me.moiclathy.edb.core.plugin

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.execution.TaskHandler
import me.moiclathy.edb.core.task.execution.TaskHandlerRegistry
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.result.TaskResult
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerSource
import me.moiclathy.edb.plugin.api.PluginExecutionContext
import me.moiclathy.edb.plugin.api.PluginTaskHandler
import me.moiclathy.edb.plugin.api.PluginTaskRequest
import me.moiclathy.edb.plugin.api.PluginWorkflowStepHandler
import me.moiclathy.edb.plugin.api.PluginWorkflowStepRequest
import me.moiclathy.edb.plugin.api.PluginWorkflowStepResult
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhrasePattern
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhrasePatternRegistry
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhraseToken
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhraseTokenType

data class PluginRegistrationResult(
    val pluginIds: Set<String>,
    val taskTypes: Set<String>,
    val workflowStepTypes: Set<String>
)

class PluginRegistrationException(message: String) : IllegalStateException(message)

class PluginRegistryAdapter(
    private val taskHandlers: TaskHandlerRegistry,
    private val workflowStepHandlers: WorkflowStepHandlerRegistry,
    private val phrasePatterns: WorkflowPhrasePatternRegistry? = null
) {
    fun register(plugins: Iterable<LoadedPlugin>): PluginRegistrationResult {
        val loaded = plugins.toList()
        val tasks = loaded.flatMap { plugin -> plugin.contributions.taskHandlers.map { plugin.descriptor.id to it } }
        val workflows = loaded.flatMap { plugin ->
            plugin.contributions.workflowStepHandlers.map { plugin.descriptor.id to it }
        }
        val existingTasks = taskHandlers.registeredTypes()
        val existingWorkflows = workflowStepHandlers.registeredTypes()
        tasks.firstOrNull { (_, handler) -> handler.taskType in existingTasks }?.let { (pluginId, handler) ->
            throw PluginRegistrationException(
                "Plugin '$pluginId' task type conflicts with an existing handler: ${handler.taskType}"
            )
        }
        workflows.firstOrNull { (_, handler) -> handler.type in existingWorkflows }?.let { (pluginId, handler) ->
            throw PluginRegistrationException(
                "Plugin '$pluginId' workflow step conflicts with an existing handler: ${handler.type}"
            )
        }

        tasks.forEach { (_, handler) -> taskHandlers.register(PluginTaskHandlerAdapter(handler)) }
        workflows.forEach { (_, handler) ->
            workflowStepHandlers.register(PluginWorkflowStepHandlerAdapter(handler))
            handler.phrasePatterns.forEach { pattern ->
                phrasePatterns?.register(WorkflowPhrasePattern(pattern.id, handler.type, pattern.version, pattern.tokens.map { token ->
                    WorkflowPhraseToken(WorkflowPhraseTokenType.valueOf(token.type.name), token.name, token.i18nKey, token.source, token.optional)
                }))
            }
        }
        return PluginRegistrationResult(
            pluginIds = loaded.mapTo(linkedSetOf()) { it.descriptor.id },
            taskTypes = tasks.mapTo(linkedSetOf()) { it.second.taskType },
            workflowStepTypes = workflows.mapTo(linkedSetOf()) { it.second.type }
        )
    }
}

internal class PluginTaskHandlerAdapter(
    private val delegate: PluginTaskHandler
) : TaskHandler {
    override val taskType: String = delegate.taskType

    override suspend fun execute(task: Task, context: TaskExecutionContext): TaskResult? {
        val result = delegate.execute(
            PluginTaskRequest(
                taskId = task.id,
                projectId = task.projectId,
                triggerUserId = task.triggerUserId,
                attemptCount = task.attemptCount,
                configSnapshot = task.configSnapshot
            ),
            object : PluginExecutionContext {
                override suspend fun ensureNotCancelled() = context.ensureNotCancelled()

                override suspend fun reportProgress(percent: Int, message: String?) {
                    context.reportProgress(percent, message)
                }
            }
        ) ?: return null
        return TaskResult(result.outputPath, result.contentType, result.metadata)
    }
}

internal class PluginWorkflowStepHandlerAdapter(
    private val delegate: PluginWorkflowStepHandler
) : WorkflowStepHandler {
    override val type: String = delegate.type
    override val source: WorkflowStepHandlerSource = WorkflowStepHandlerSource.PLUGIN

    override suspend fun execute(
        step: WorkflowStepDefinition,
        context: PipelineContext
    ): PipelineStepResult = when (
        val result = delegate.execute(
            PluginWorkflowStepRequest(
                stepId = step.id,
                config = step.config,
                context = context.asMap().mapValues { (_, value) -> value?.toString().orEmpty() }
            )
        )
    ) {
        is PluginWorkflowStepResult.Success -> PipelineStepResult.Success(context.withValues(result.values))
        is PluginWorkflowStepResult.Failure -> PipelineStepResult.Failure(
            result.code,
            result.message,
            result.retryable
        )
    }
}
