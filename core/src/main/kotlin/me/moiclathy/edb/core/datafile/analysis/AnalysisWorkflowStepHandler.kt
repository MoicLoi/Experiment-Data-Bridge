package me.moiclathy.edb.core.datafile.analysis

import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.PipelineSystemContextKeys
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandler

class AnalysisWorkflowStepHandler(
    private val executionService: AnalysisExecutionService
) : WorkflowStepHandler {
    override val type: String = TYPE

    override suspend fun execute(step: WorkflowStepDefinition, context: PipelineContext): PipelineStepResult {
        val handlerId = step.config["handlerId"]?.takeIf { it.isNotBlank() }
            ?: return analysisInvalidConfig(step, "handlerId")
        val projectId = context.getAs<Int>(PipelineSystemContextKeys.PROJECT_ID)
            ?: return analysisMissingContext(step, PipelineSystemContextKeys.PROJECT_ID)
        val requestedBy = context.getAs<Int>(PipelineSystemContextKeys.TRIGGER_USER_ID)
            ?: return analysisMissingContext(step, PipelineSystemContextKeys.TRIGGER_USER_ID)
        val taskId = context.getAs<Int>(PipelineSystemContextKeys.TASK_ID)
            ?: return analysisMissingContext(step, PipelineSystemContextKeys.TASK_ID)

        return try {
            val staticSources = parseAnalysisSourceIds(step.config["sourceDataFileIds"].orEmpty())
            val contextKey = step.config["sourceDataFileIdsContext"]?.takeIf { it.isNotBlank() }
            val contextSources = if (contextKey == null) emptySet() else {
                val value = context[contextKey]
                    ?: return PipelineStepResult.Failure(
                        "WORKFLOW_CONTEXT_VALUE_MISSING",
                        "Workflow step ${step.id} cannot read analysis source ids from context: $contextKey"
                    )
                parseAnalysisSourceIds(value.toString())
            }
            val sourceIds = staticSources + contextSources
            require(sourceIds.isNotEmpty()) { "Workflow step ${step.id} requires analysis source DataFile ids." }
            val parameters = step.config.filterKeys { it.startsWith(PARAMETER_PREFIX) }
                .mapKeys { it.key.removePrefix(PARAMETER_PREFIX) }
                .toMutableMap()
            step.config.filterKeys { it.startsWith(PARAMETER_CONTEXT_PREFIX) }
                .forEach { (key, contextValueKey) ->
                    val name = key.removePrefix(PARAMETER_CONTEXT_PREFIX)
                    val value = context[contextValueKey]
                        ?: return PipelineStepResult.Failure(
                            "WORKFLOW_CONTEXT_VALUE_MISSING",
                            "Workflow step ${step.id} cannot read analysis parameter '$name' from context: $contextValueKey"
                        )
                    parameters[name] = value.toString()
                }
            val execution = executionService.execute(
                ExecuteAnalysisCommand(projectId, handlerId, sourceIds, requestedBy, taskId, parameters)
            )
            val resultKey = step.config["resultContextKey"]?.takeIf { it.isNotBlank() }
                ?: DEFAULT_RESULT_CONTEXT_KEY
            PipelineStepResult.Success(context
                .withValue(resultKey, requireNotNull(execution.artifact.id))
                .withValue("${resultKey}ProvenanceReportId", requireNotNull(execution.provenanceReport.id)))
        } catch (failure: Exception) {
            PipelineStepResult.Failure("ANALYSIS_FAILED", failure.message ?: "Workflow analysis failed.")
        }
    }

    companion object {
        const val TYPE = "datafile.analysis.run"
        const val DEFAULT_RESULT_CONTEXT_KEY = "analysisDataFileId"
        private const val PARAMETER_PREFIX = "parameter."
        private const val PARAMETER_CONTEXT_PREFIX = "parameterContext."
    }
}

private fun analysisInvalidConfig(step: WorkflowStepDefinition, property: String) = PipelineStepResult.Failure(
    "WORKFLOW_STEP_CONFIG_INVALID",
    "Workflow step ${step.id} requires a non-blank '$property' configuration value."
)

private fun analysisMissingContext(step: WorkflowStepDefinition, key: String) = PipelineStepResult.Failure(
    "WORKFLOW_TASK_CONTEXT_MISSING",
    "Workflow step ${step.id} requires pipeline system context: $key"
)
