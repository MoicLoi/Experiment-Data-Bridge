package me.moiclathy.edb.core.task.workflow.ir

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.moiclathy.edb.core.task.pipeline.Pipeline
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStep
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.schema.WorkflowJsonSchemaViolation
import me.moiclathy.edb.core.task.workflow.schema.WorkflowJsonSchemaValidator
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepParameterSchema
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepSchemaRegistry

class WorkflowIrCompiler(
    private val handlers: WorkflowStepHandlerRegistry,
    private val validator: WorkflowIrValidator = WorkflowIrValidator(),
    private val schemas: WorkflowStepSchemaRegistry = WorkflowStepSchemaRegistry(),
    private val parameterValidator: WorkflowJsonSchemaValidator = WorkflowJsonSchemaValidator()
) {
    fun compile(workflow: WorkflowIr): Pipeline {
        validator.validate(workflow)
        val ordered = validator.executionOrder(workflow.steps)
        val steps = ordered.mapIndexed { index, step ->
            val handler = handlers.require(step.type, step.version)
            val parameterSchema = schemas.find(step.type, step.version)
            parameterSchema?.let { validateAtCompileTime(step, it) }
            PipelineStep(step.id) { context ->
                val prepared = if (index == 0) prepareInputs(workflow, context) else PipelineStepResult.Success(context)
                if (prepared is PipelineStepResult.Failure) return@PipelineStep prepared
                val result = execute(step, handler, parameterSchema, (prepared as PipelineStepResult.Success).context)
                if (index == ordered.lastIndex && result is PipelineStepResult.Success) {
                    validateOutputs(workflow, result.context)
                } else result
            }
        }
        return Pipeline(workflow.id, steps)
    }

    private suspend fun execute(
        step: WorkflowIrStep,
        handler: WorkflowStepHandler,
        parameterSchema: WorkflowStepParameterSchema?,
        context: PipelineContext
    ): PipelineStepResult {
        var attempt = 1
        var delayMs = step.policy.retry.initialDelayMs
        while (true) {
            val attemptResult = executeAttempt(step, handler, parameterSchema, context)
            val result = if (attemptResult is PipelineStepResult.Success) {
                bindOutputs(step, attemptResult.context)
            } else attemptResult
            if (result is PipelineStepResult.Success) return result
            val failure = result as PipelineStepResult.Failure
            if (!failure.retryable || attempt >= step.policy.retry.maxAttempts) {
                return applyFailureStrategy(step, context, failure, attempt)
            }
            if (delayMs > 0) delay(delayMs)
            delayMs = nextDelay(delayMs, step.policy.retry)
            attempt++
        }
    }

    private fun prepareInputs(workflow: WorkflowIr, context: PipelineContext): PipelineStepResult {
        var prepared = context
        workflow.inputs.forEach { input ->
            if (!prepared.contains(input.contextKey)) {
                when {
                    input.defaultValue != null -> prepared = prepared.withValue(input.contextKey, input.defaultValue.toContextValue())
                    input.required -> return PipelineStepResult.Failure(
                        "WORKFLOW_INPUT_MISSING",
                        "Required workflow input '${input.name}' is missing from context key '${input.contextKey}'."
                    )
                }
            }
        }
        return PipelineStepResult.Success(prepared)
    }

    private fun validateOutputs(workflow: WorkflowIr, context: PipelineContext): PipelineStepResult {
        val missing = workflow.outputs.firstOrNull { it.required && !context.contains(it.contextKey) }
        return if (missing == null) PipelineStepResult.Success(context) else PipelineStepResult.Failure(
            "WORKFLOW_OUTPUT_MISSING",
            "Required workflow output '${missing.name}' is missing from context key '${missing.contextKey}'."
        )
    }

    private suspend fun executeAttempt(
        step: WorkflowIrStep,
        handler: WorkflowStepHandler,
        parameterSchema: WorkflowStepParameterSchema?,
        context: PipelineContext
    ): PipelineStepResult {
        val parameters = try {
            bindParameters(step, context)
        } catch (failure: WorkflowIrBindingException) {
            return PipelineStepResult.Failure("WORKFLOW_BINDING_FAILED", failure.message.orEmpty())
        }
        parameterSchema?.let { schema ->
            val violations = parameterValidator.validate(parameters, schema.schema)
            if (violations.isNotEmpty()) return PipelineStepResult.Failure(
                "WORKFLOW_STEP_PARAMETERS_INVALID",
                violations.joinToString("; ") { "${it.instancePath}: ${it.message}" }
            )
        }
        val definition = WorkflowStepDefinition(
            id = step.id,
            type = step.type,
            config = parameters.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.let { key to it.content }
            }.toMap(),
            version = step.version,
            parameters = parameters
        )
        return try {
            step.policy.timeoutMs?.let { timeout ->
                withTimeout(timeout) { handler.execute(definition, context) }
            } ?: handler.execute(definition, context)
        } catch (_: TimeoutCancellationException) {
            PipelineStepResult.Failure(
                code = "WORKFLOW_STEP_TIMEOUT",
                message = "Workflow step '${step.id}' exceeded ${step.policy.timeoutMs} ms.",
                retryable = true
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            PipelineStepResult.Failure(
                code = "WORKFLOW_STEP_EXCEPTION",
                message = failure.message ?: "Workflow step '${step.id}' failed unexpectedly.",
                retryable = true
            )
        }
    }

    private fun validateAtCompileTime(step: WorkflowIrStep, schema: WorkflowStepParameterSchema) {
        val violations = parameterValidator.validate(
            step.parameters,
            schema.schema,
            step.contextBindings.mapTo(linkedSetOf()) { it.parameterPath }
        )
        if (violations.isNotEmpty()) throw WorkflowStepParameterValidationException(
            step.id,
            step.type,
            step.version,
            violations
        )
    }

    private fun bindParameters(step: WorkflowIrStep, context: PipelineContext): JsonObject {
        var parameters = step.parameters
        step.contextBindings.forEach { binding ->
            if (!context.contains(binding.contextKey)) {
                if (binding.required) throw WorkflowIrBindingException(
                    "Required context value '${binding.contextKey}' is missing for step '${step.id}'."
                )
            } else {
                parameters = parameters.withPointer(binding.parameterPath, context[binding.contextKey].toJsonElement())
            }
        }
        return parameters
    }

    private fun bindOutputs(step: WorkflowIrStep, context: PipelineContext): PipelineStepResult {
        var result = context
        step.outputBindings.forEach { binding ->
            if (!context.contains(binding.sourceContextKey)) {
                if (binding.required) return PipelineStepResult.Failure(
                    "WORKFLOW_OUTPUT_MISSING",
                    "Required output '${binding.sourceContextKey}' is missing after step '${step.id}'."
                )
            } else {
                result = result.withValue(binding.targetContextKey, context[binding.sourceContextKey])
            }
        }
        return PipelineStepResult.Success(result)
    }

    private fun applyFailureStrategy(
        step: WorkflowIrStep,
        context: PipelineContext,
        failure: PipelineStepResult.Failure,
        attempts: Int
    ): PipelineStepResult = when (step.policy.failureStrategy) {
        WorkflowIrFailureStrategy.STOP_WORKFLOW -> failure
        WorkflowIrFailureStrategy.CONTINUE -> PipelineStepResult.Success(
            context.withValue(
                "$FAILURE_CONTEXT_PREFIX${step.id}",
                mapOf(
                    "code" to failure.code,
                    "message" to failure.message,
                    "retryable" to failure.retryable,
                    "attempts" to attempts
                )
            )
        )
    }

    private fun nextDelay(current: Long, retry: WorkflowIrRetryPolicy): Long {
        if (current == 0L || retry.maxDelayMs == 0L) return 0L
        return (current.toDouble() * retry.backoffMultiplier)
            .coerceAtMost(retry.maxDelayMs.toDouble())
            .toLong()
    }

    companion object {
        const val FAILURE_CONTEXT_PREFIX = "workflow.failures."
    }
}

class WorkflowIrBindingException(message: String) : IllegalArgumentException(message)

class WorkflowStepParameterValidationException(
    val stepId: String,
    val stepType: String,
    val stepVersion: String,
    val violations: List<WorkflowJsonSchemaViolation>
) : IllegalArgumentException(
    "Workflow step '$stepId' parameters do not match $stepType@$stepVersion: " +
        violations.joinToString("; ") { "${it.instancePath}: ${it.message}" }
)

private fun JsonObject.withPointer(pointer: String, value: JsonElement): JsonObject {
    val segments = pointer.split('/').drop(1).map { it.replace("~1", "/").replace("~0", "~") }
    fun write(current: JsonObject, index: Int): JsonObject {
        val key = segments[index]
        if (index == segments.lastIndex) return JsonObject(current + (key to value))
        val child = current[key]
        if (child != null && child !is JsonObject) {
            throw WorkflowIrBindingException("Cannot bind $pointer because '$key' is not an object.")
        }
        return JsonObject(current + (key to write(child as? JsonObject ?: JsonObject(emptyMap()), index + 1)))
    }
    return write(this, 0)
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Byte -> JsonPrimitive(this)
    is Short -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) ->
        (key as? String ?: throw WorkflowIrBindingException("JSON object context keys must be strings.")) to value.toJsonElement()
    })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    else -> throw WorkflowIrBindingException("Context value of type ${this::class.simpleName} cannot be bound to JSON.")
}

private fun JsonElement.toContextValue(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> mapValues { it.value.toContextValue() }
    is JsonArray -> map { it.toContextValue() }
    is JsonPrimitive -> when {
        isString -> content
        content == "true" || content == "false" -> content.toBooleanStrict()
        else -> content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
    }
}
