package me.moiclathy.edb.core.datafile.report

import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.PipelineSystemContextKeys
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandler

class ReportWorkflowStepHandler(
    private val definitions: ReportDefinitionRegistry,
    private val artifactService: ReportArtifactService
) : WorkflowStepHandler {
    override val type: String = TYPE

    override suspend fun execute(step: WorkflowStepDefinition, context: PipelineContext): PipelineStepResult {
        val definitionId = step.config["definitionId"]?.takeIf { it.isNotBlank() }
            ?: return invalid(step, "definitionId")
        val projectId = context.getAs<Int>(PipelineSystemContextKeys.PROJECT_ID)
            ?: return missingSystemContext(step, PipelineSystemContextKeys.PROJECT_ID)
        val requestedBy = context.getAs<Int>(PipelineSystemContextKeys.TRIGGER_USER_ID)
            ?: return missingSystemContext(step, PipelineSystemContextKeys.TRIGGER_USER_ID)
        val taskId = context.getAs<Int>(PipelineSystemContextKeys.TASK_ID)
            ?: return missingSystemContext(step, PipelineSystemContextKeys.TASK_ID)

        return try {
            val staticSources = ReportTaskPayloadCodec.parseDataFileIds(
                step.config["sourceDataFileIds"].orEmpty()
            )
            val contextSourceKey = step.config["sourceDataFileIdsContext"]?.takeIf { it.isNotBlank() }
            val contextSources = if (contextSourceKey == null) {
                emptySet()
            } else {
                val value = context[contextSourceKey]
                    ?: return PipelineStepResult.Failure(
                        "WORKFLOW_CONTEXT_VALUE_MISSING",
                        "Workflow step ${step.id} cannot read source DataFile ids from context: $contextSourceKey"
                    )
                ReportTaskPayloadCodec.parseDataFileIds(value.toString())
            }
            val parameters = step.config
                .filterKeys { it.startsWith(PARAMETER_PREFIX) }
                .mapKeys { it.key.removePrefix(PARAMETER_PREFIX) }
                .toMutableMap()
            step.config.filterKeys { it.startsWith(PARAMETER_CONTEXT_PREFIX) }
                .forEach { (key, contextKey) ->
                    val name = key.removePrefix(PARAMETER_CONTEXT_PREFIX)
                    val value = context[contextKey]
                        ?: return PipelineStepResult.Failure(
                            "WORKFLOW_CONTEXT_VALUE_MISSING",
                            "Workflow step ${step.id} cannot read report parameter '$name' from context: $contextKey"
                        )
                    parameters[name] = value.toString()
                }
            val artifact = artifactService.generate(
                GenerateReportArtifactCommand(
                    projectId = projectId,
                    definition = definitions.require(definitionId),
                    sourceDataFileIds = staticSources + contextSources,
                    requestedBy = requestedBy,
                    producingTaskId = taskId,
                    parameters = parameters
                )
            )
            val resultKey = step.config["resultContextKey"]?.takeIf { it.isNotBlank() }
                ?: DEFAULT_RESULT_CONTEXT_KEY
            PipelineStepResult.Success(context.withValue(resultKey, requireNotNull(artifact.id)))
        } catch (failure: Exception) {
            PipelineStepResult.Failure(
                code = "REPORT_GENERATION_FAILED",
                message = failure.message ?: "Workflow report generation failed."
            )
        }
    }

    companion object {
        const val TYPE = "datafile.report.generate"
        const val DEFAULT_RESULT_CONTEXT_KEY = "reportDataFileId"
        private const val PARAMETER_PREFIX = "parameter."
        private const val PARAMETER_CONTEXT_PREFIX = "parameterContext."
    }
}

private fun invalid(step: WorkflowStepDefinition, property: String) = PipelineStepResult.Failure(
    "WORKFLOW_STEP_CONFIG_INVALID",
    "Workflow step ${step.id} requires a non-blank '$property' configuration value."
)

private fun missingSystemContext(step: WorkflowStepDefinition, key: String) = PipelineStepResult.Failure(
    "WORKFLOW_TASK_CONTEXT_MISSING",
    "Workflow step ${step.id} requires pipeline system context: $key"
)
