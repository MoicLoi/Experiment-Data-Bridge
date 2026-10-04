package me.moiclathy.edb.core.task.workflow.ir

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.moiclathy.edb.core.task.workflow.WorkflowDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowValidator

/** Preserves the original list-order and string configuration semantics through Workflow IR v1. */
class LegacyWorkflowIrAdapter(
    private val validator: WorkflowValidator = WorkflowValidator()
) {
    fun convert(definition: WorkflowDefinition): WorkflowIr {
        validator.validate(definition)
        return WorkflowIr(
            id = definition.id,
            version = "legacy-${definition.schemaVersion}",
            description = definition.name,
            steps = definition.steps.mapIndexed { index, step ->
                WorkflowIrStep(
                    id = step.id,
                    type = step.type,
                    version = step.version,
                    order = index,
                    dependsOn = if (index == 0) emptySet() else setOf(definition.steps[index - 1].id),
                    parameters = JsonObject(step.config.mapValues { JsonPrimitive(it.value) })
                )
            }
        )
    }
}
