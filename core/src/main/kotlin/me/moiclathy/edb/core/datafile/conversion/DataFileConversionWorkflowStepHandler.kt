package me.moiclathy.edb.core.datafile.conversion

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.PipelineSystemContextKeys
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandler

class DataFileConversionWorkflowStepHandler(
    private val conversionService: DataFileConversionService
) : WorkflowStepHandler {
    override val type: String = TYPE

    override suspend fun execute(
        step: WorkflowStepDefinition,
        context: PipelineContext
    ): PipelineStepResult {
        val targetName = step.config["targetFormat"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: return invalidConfig(step, "targetFormat")
        val targetFormat = DataFileFormat.entries.firstOrNull {
            it.name.equals(targetName, ignoreCase = true)
        } ?: return PipelineStepResult.Failure(
            "WORKFLOW_STEP_CONFIG_INVALID",
            "Workflow step ${step.id} has an unknown targetFormat: $targetName"
        )
        val projectId = context.getAs<Int>(PipelineSystemContextKeys.PROJECT_ID)
            ?: return missingSystemContext(step, PipelineSystemContextKeys.PROJECT_ID)
        val requestedBy = context.getAs<Int>(PipelineSystemContextKeys.TRIGGER_USER_ID)
            ?: return missingSystemContext(step, PipelineSystemContextKeys.TRIGGER_USER_ID)
        val taskId = context.getAs<Int>(PipelineSystemContextKeys.TASK_ID)
            ?: return missingSystemContext(step, PipelineSystemContextKeys.TASK_ID)

        return try {
            val sourceDataFileId = resolveSourceDataFileId(step, context)
                ?: return PipelineStepResult.Failure(
                    "WORKFLOW_STEP_CONFIG_INVALID",
                    "Workflow step ${step.id} requires sourceDataFileId or sourceDataFileIdContext."
                )
            val options = step.config.filterKeys { it.startsWith(OPTION_PREFIX) }
                .mapKeys { it.key.removePrefix(OPTION_PREFIX) }
                .toMutableMap()
            step.config.filterKeys { it.startsWith(OPTION_CONTEXT_PREFIX) }
                .forEach { (key, contextKey) ->
                    val optionName = key.removePrefix(OPTION_CONTEXT_PREFIX)
                    val value = context[contextKey]
                        ?: return PipelineStepResult.Failure(
                            "WORKFLOW_CONTEXT_VALUE_MISSING",
                            "Workflow step ${step.id} cannot read conversion option '$optionName' from context: $contextKey"
                        )
                    options[optionName] = value.toString()
                }
            val execution = conversionService.convert(
                ExecuteDataFileConversion(
                    projectId = projectId,
                    sourceDataFileId = sourceDataFileId,
                    targetFormat = targetFormat,
                    requestedBy = requestedBy,
                    producingTaskId = taskId,
                    options = options
                )
            )
            val resultKey = step.config["resultContextKey"]?.takeIf { it.isNotBlank() }
                ?: DEFAULT_RESULT_CONTEXT_KEY
            PipelineStepResult.Success(context
                .withValue(resultKey, requireNotNull(execution.artifact.id))
                .withValue("${resultKey}ProvenanceReportId", requireNotNull(execution.provenanceReport.id)))
        } catch (failure: Exception) {
            PipelineStepResult.Failure(
                code = "DATA_FILE_CONVERSION_FAILED",
                message = failure.message ?: "Workflow DataFile conversion failed."
            )
        }
    }

    private fun resolveSourceDataFileId(
        step: WorkflowStepDefinition,
        context: PipelineContext
    ): Int? {
        val staticValue = step.config["sourceDataFileId"]?.takeIf { it.isNotBlank() }
        val contextKey = step.config["sourceDataFileIdContext"]?.takeIf { it.isNotBlank() }
        require(staticValue == null || contextKey == null) {
            "Workflow step ${step.id} cannot define both sourceDataFileId and sourceDataFileIdContext."
        }
        val raw = staticValue ?: contextKey?.let { key ->
            context[key] ?: throw IllegalArgumentException(
                "Workflow step ${step.id} cannot read source DataFile id from context: $key"
            )
        } ?: return null
        return raw.toString().toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Workflow step ${step.id} has an invalid source DataFile id: $raw")
    }

    companion object {
        const val TYPE = "datafile.convert"
        const val DEFAULT_RESULT_CONTEXT_KEY = "convertedDataFileId"
        private const val OPTION_PREFIX = "option."
        private const val OPTION_CONTEXT_PREFIX = "optionContext."
    }
}

private fun invalidConfig(step: WorkflowStepDefinition, property: String) = PipelineStepResult.Failure(
    "WORKFLOW_STEP_CONFIG_INVALID",
    "Workflow step ${step.id} requires a non-blank '$property' configuration value."
)

private fun missingSystemContext(step: WorkflowStepDefinition, key: String) = PipelineStepResult.Failure(
    "WORKFLOW_TASK_CONTEXT_MISSING",
    "Workflow step ${step.id} requires pipeline system context: $key"
)
