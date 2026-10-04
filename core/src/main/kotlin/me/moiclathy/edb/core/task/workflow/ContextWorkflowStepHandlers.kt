package me.moiclathy.edb.core.task.workflow

import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult

/** Sets a string value in the pipeline context. Config: `key`, `value`. */
class SetContextWorkflowStepHandler : WorkflowStepHandler {
    override val type: String = TYPE

    override suspend fun execute(
        step: WorkflowStepDefinition,
        context: PipelineContext
    ): PipelineStepResult {
        val key = step.requiredConfig("key") ?: return invalidConfig(step, "key")
        val value = step.config["value"] ?: return invalidConfig(step, "value")
        return PipelineStepResult.Success(context.withValue(key, value))
    }

    companion object {
        const val TYPE = "context.set"
    }
}

/** Copies an existing context value. Config: `from`, `to`. */
class CopyContextWorkflowStepHandler : WorkflowStepHandler {
    override val type: String = TYPE

    override suspend fun execute(
        step: WorkflowStepDefinition,
        context: PipelineContext
    ): PipelineStepResult {
        val from = step.requiredConfig("from") ?: return invalidConfig(step, "from")
        val to = step.requiredConfig("to") ?: return invalidConfig(step, "to")
        if (!context.contains(from)) {
            return PipelineStepResult.Failure(
                code = "WORKFLOW_CONTEXT_VALUE_MISSING",
                message = "Workflow step ${step.id} cannot copy missing context value: $from"
            )
        }
        return PipelineStepResult.Success(context.withValue(to, context[from]))
    }

    companion object {
        const val TYPE = "context.copy"
    }
}

private fun WorkflowStepDefinition.requiredConfig(name: String): String? =
    config[name]?.takeIf { it.isNotBlank() }

private fun invalidConfig(
    step: WorkflowStepDefinition,
    property: String
) = PipelineStepResult.Failure(
    code = "WORKFLOW_STEP_CONFIG_INVALID",
    message = "Workflow step ${step.id} requires a non-blank '$property' configuration value."
)

