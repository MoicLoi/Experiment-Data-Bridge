package me.moiclathy.edb.core.task.workflow.ir

import me.moiclathy.edb.core.task.workflow.WorkflowValidationError
import me.moiclathy.edb.core.task.workflow.WorkflowValidationException

class WorkflowIrValidator {
    fun validate(workflow: WorkflowIr): WorkflowIr {
        val errors = buildList {
            if (workflow.schemaVersion != WorkflowIr.CURRENT_SCHEMA_VERSION) {
                add(error("$.schemaVersion", "Unsupported Workflow IR version ${workflow.schemaVersion}."))
            }
            identifier(workflow.id, "$.id", "Workflow id", this)
            identifier(workflow.version, "$.version", "Workflow version", this)
            workflow.description?.takeIf { it.isBlank() }?.let {
                add(error("$.description", "Workflow description must not be blank."))
            }
            if (workflow.steps.isEmpty()) add(error("$.steps", "Workflow IR must contain at least one step."))
            validateValues(workflow.inputs, workflow.outputs, this)
            validateSteps(workflow.steps, this)
        }
        if (errors.isNotEmpty()) throw WorkflowValidationException(errors)
        return workflow
    }

    private fun validateValues(
        inputs: List<WorkflowIrInput>,
        outputs: List<WorkflowIrOutput>,
        errors: MutableList<WorkflowValidationError>
    ) {
        inputs.forEachIndexed { index, input ->
            identifier(input.name, "$.inputs[$index].name", "Input name", errors)
            contextKey(input.contextKey, "$.inputs[$index].contextKey", errors)
            input.description?.takeIf { it.isBlank() }?.let {
                errors += error("$.inputs[$index].description", "Input description must not be blank.")
            }
        }
        outputs.forEachIndexed { index, output ->
            identifier(output.name, "$.outputs[$index].name", "Output name", errors)
            contextKey(output.contextKey, "$.outputs[$index].contextKey", errors)
            output.description?.takeIf { it.isBlank() }?.let {
                errors += error("$.outputs[$index].description", "Output description must not be blank.")
            }
        }
        duplicatePaths(inputs.map { it.name }, "$.inputs", "input name", errors)
        duplicatePaths(outputs.map { it.name }, "$.outputs", "output name", errors)
    }

    private fun validateSteps(steps: List<WorkflowIrStep>, errors: MutableList<WorkflowValidationError>) {
        val ids = steps.map { it.id }.toSet()
        duplicatePaths(steps.map { it.id }, "$.steps", "step id", errors)
        duplicatePaths(steps.map { it.order.toString() }, "$.steps", "step order", errors)
        steps.forEachIndexed { index, step ->
            val path = "$.steps[$index]"
            identifier(step.id, "$path.id", "Step id", errors)
            identifier(step.type, "$path.type", "Step type", errors)
            identifier(step.version, "$path.version", "Step version", errors)
            if (step.order < 0) errors += error("$path.order", "Step order must not be negative.")
            step.dependsOn.forEach { dependency ->
                when {
                    dependency == step.id -> errors += error("$path.dependsOn", "Step cannot depend on itself.")
                    dependency !in ids -> errors += error("$path.dependsOn", "Unknown dependency: $dependency")
                }
            }
            step.parameters.keys.filter { it.isBlank() }.forEach {
                errors += error("$path.parameters", "Parameter names must not be blank.")
            }
            step.contextBindings.forEachIndexed { bindingIndex, binding ->
                val bindingPath = "$path.contextBindings[$bindingIndex]"
                jsonPointer(binding.parameterPath, "$bindingPath.parameterPath", errors)
                contextKey(binding.contextKey, "$bindingPath.contextKey", errors)
            }
            duplicatePaths(
                step.contextBindings.map { it.parameterPath },
                "$path.contextBindings",
                "parameter path",
                errors
            )
            step.outputBindings.forEachIndexed { bindingIndex, binding ->
                val bindingPath = "$path.outputBindings[$bindingIndex]"
                contextKey(binding.sourceContextKey, "$bindingPath.sourceContextKey", errors)
                contextKey(binding.targetContextKey, "$bindingPath.targetContextKey", errors)
            }
            duplicatePaths(
                step.outputBindings.map { it.targetContextKey },
                "$path.outputBindings",
                "target context key",
                errors
            )
            validatePolicy(step.policy, "$path.policy", errors)
        }
        findCycle(steps)?.let { cycle ->
            errors += error("$.steps", "Workflow dependencies contain a cycle: ${cycle.joinToString(" -> ")}")
        }
    }

    private fun validatePolicy(
        policy: WorkflowIrStepPolicy,
        path: String,
        errors: MutableList<WorkflowValidationError>
    ) {
        if (policy.timeoutMs != null && policy.timeoutMs <= 0) {
            errors += error("$path.timeoutMs", "Step timeout must be greater than zero.")
        }
        val retry = policy.retry
        if (retry.maxAttempts < 1) errors += error("$path.retry.maxAttempts", "Maximum attempts must be at least one.")
        if (retry.initialDelayMs < 0) errors += error("$path.retry.initialDelayMs", "Initial delay must not be negative.")
        if (!retry.backoffMultiplier.isFinite() || retry.backoffMultiplier < 1.0) {
            errors += error("$path.retry.backoffMultiplier", "Backoff multiplier must be finite and at least one.")
        }
        if (retry.maxDelayMs < retry.initialDelayMs) {
            errors += error("$path.retry.maxDelayMs", "Maximum delay must not be shorter than the initial delay.")
        }
    }

    internal fun executionOrder(steps: List<WorkflowIrStep>): List<WorkflowIrStep> {
        val remainingDependencies = steps.associate { it.id to it.dependsOn.toMutableSet() }.toMutableMap()
        val byId = steps.associateBy { it.id }
        val result = mutableListOf<WorkflowIrStep>()
        while (remainingDependencies.isNotEmpty()) {
            val ready = remainingDependencies.filterValues { it.isEmpty() }.keys
                .mapNotNull(byId::get)
                .sortedWith(compareBy<WorkflowIrStep> { it.order }.thenBy { it.id })
            if (ready.isEmpty()) throw WorkflowValidationException(
                listOf(error("$.steps", "Workflow dependencies contain a cycle."))
            )
            ready.forEach { step ->
                result += step
                remainingDependencies.remove(step.id)
                remainingDependencies.values.forEach { it.remove(step.id) }
            }
        }
        return result
    }

    private fun findCycle(steps: List<WorkflowIrStep>): List<String>? {
        val dependencies = steps.associate { it.id to it.dependsOn.filterTo(mutableSetOf()) { id -> steps.any { it.id == id } } }
        val visiting = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(id: String): List<String>? {
            if (id in visiting) return (visiting.dropWhile { it != id } + id)
            if (!visited.add(id)) return null
            visiting += id
            dependencies[id].orEmpty().forEach { dependency -> visit(dependency)?.let { return it } }
            visiting -= id
            return null
        }
        return dependencies.keys.firstNotNullOfOrNull(::visit)
    }

    private fun identifier(value: String, path: String, label: String, errors: MutableList<WorkflowValidationError>) {
        when {
            value.isBlank() -> errors += error(path, "$label must not be blank.")
            value != value.trim() -> errors += error(path, "$label must not contain surrounding whitespace.")
        }
    }

    private fun contextKey(value: String, path: String, errors: MutableList<WorkflowValidationError>) =
        identifier(value, path, "Context key", errors)

    private fun jsonPointer(value: String, path: String, errors: MutableList<WorkflowValidationError>) {
        if (!value.startsWith("/") || value.length == 1 || value.split('/').drop(1).any { it.isEmpty() }) {
            errors += error(path, "Parameter path must be a non-empty object JSON Pointer.")
        }
    }

    private fun duplicatePaths(
        values: List<String>,
        path: String,
        label: String,
        errors: MutableList<WorkflowValidationError>
    ) {
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().forEach {
            errors += error(path, "Duplicate $label: $it")
        }
    }

    private fun error(path: String, message: String) = WorkflowValidationError(path, message)
}
