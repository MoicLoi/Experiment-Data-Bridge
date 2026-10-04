package me.moiclathy.edb.core.task.workflow.ir

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class WorkflowIr(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val version: String,
    val description: String? = null,
    val inputs: List<WorkflowIrInput> = emptyList(),
    val outputs: List<WorkflowIrOutput> = emptyList(),
    val steps: List<WorkflowIrStep>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class WorkflowIrInput(
    val name: String,
    val contextKey: String = name,
    val required: Boolean = true,
    val description: String? = null,
    val defaultValue: JsonElement? = null
)

data class WorkflowIrOutput(
    val name: String,
    val contextKey: String = name,
    val required: Boolean = true,
    val description: String? = null
)

data class WorkflowIrStep(
    val id: String,
    val type: String,
    val version: String,
    val order: Int,
    val dependsOn: Set<String> = emptySet(),
    val parameters: JsonObject = JsonObject(emptyMap()),
    val contextBindings: List<WorkflowIrContextBinding> = emptyList(),
    val outputBindings: List<WorkflowIrOutputBinding> = emptyList(),
    val policy: WorkflowIrStepPolicy = WorkflowIrStepPolicy()
)

/** Writes a Pipeline context value into a step parameter using an object-only JSON Pointer. */
data class WorkflowIrContextBinding(
    val parameterPath: String,
    val contextKey: String,
    val required: Boolean = true
)

/** Copies a value produced by a handler to a stable downstream Pipeline context key. */
data class WorkflowIrOutputBinding(
    val sourceContextKey: String,
    val targetContextKey: String,
    val required: Boolean = true
)

data class WorkflowIrStepPolicy(
    val timeoutMs: Long? = null,
    val retry: WorkflowIrRetryPolicy = WorkflowIrRetryPolicy(),
    val failureStrategy: WorkflowIrFailureStrategy = WorkflowIrFailureStrategy.STOP_WORKFLOW
)

data class WorkflowIrRetryPolicy(
    val maxAttempts: Int = 1,
    val initialDelayMs: Long = 1_000,
    val backoffMultiplier: Double = 2.0,
    val maxDelayMs: Long = 60_000
)

enum class WorkflowIrFailureStrategy {
    STOP_WORKFLOW,
    CONTINUE
}
