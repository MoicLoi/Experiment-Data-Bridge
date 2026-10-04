package me.moiclathy.edb.core.task.workflow.ir

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

interface WorkflowIrCodec {
    fun serialize(workflow: WorkflowIr): String
    fun deserialize(document: String): WorkflowIr
}

class WorkflowIrJsonException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** Strict, canonical JSON codec for Workflow IR v1. */
class JsonWorkflowIrCodec(
    private val validator: WorkflowIrValidator = WorkflowIrValidator(),
    private val json: Json = Json { prettyPrint = true }
) : WorkflowIrCodec {
    override fun serialize(workflow: WorkflowIr): String {
        validator.validate(workflow)
        return json.encodeToString(JsonElement.serializer(), workflow.toJson())
    }

    override fun deserialize(document: String): WorkflowIr = try {
        val root = json.parseToJsonElement(document).objectAt("$")
        root.rejectUnknown(ROOT_FIELDS, "$")
        val workflow = WorkflowIr(
            schemaVersion = root.requiredInt("schemaVersion", "$"),
            id = root.requiredString("id", "$"),
            version = root.requiredString("version", "$"),
            description = root.optionalString("description", "$"),
            inputs = root.optionalArray("inputs", "$").mapIndexed { index, value ->
                value.objectAt("$.inputs[$index]").toInput("$.inputs[$index]")
            },
            outputs = root.optionalArray("outputs", "$").mapIndexed { index, value ->
                value.objectAt("$.outputs[$index]").toOutput("$.outputs[$index]")
            },
            steps = root.requiredArray("steps", "$").mapIndexed { index, value ->
                value.objectAt("$.steps[$index]").toStep("$.steps[$index]")
            }
        )
        validator.validate(workflow)
    } catch (failure: WorkflowIrJsonException) {
        throw failure
    } catch (failure: Exception) {
        throw WorkflowIrJsonException("Invalid Workflow IR JSON: ${failure.message}", failure)
    }

    private fun JsonObject.toInput(path: String): WorkflowIrInput {
        rejectUnknown(INPUT_FIELDS, path)
        val name = requiredString("name", path)
        return WorkflowIrInput(
            name = name,
            contextKey = optionalString("contextKey", path) ?: name,
            required = optionalBoolean("required", path) ?: true,
            description = optionalString("description", path),
            defaultValue = if (containsKey("default")) getValue("default") else null
        )
    }

    private fun JsonObject.toOutput(path: String): WorkflowIrOutput {
        rejectUnknown(OUTPUT_FIELDS, path)
        val name = requiredString("name", path)
        return WorkflowIrOutput(
            name = name,
            contextKey = optionalString("contextKey", path) ?: name,
            required = optionalBoolean("required", path) ?: true,
            description = optionalString("description", path)
        )
    }

    private fun JsonObject.toStep(path: String): WorkflowIrStep {
        rejectUnknown(STEP_FIELDS, path)
        return WorkflowIrStep(
            id = requiredString("id", path),
            type = requiredString("type", path),
            version = requiredString("version", path),
            order = requiredInt("order", path),
            dependsOn = optionalArray("dependsOn", path).mapIndexedTo(linkedSetOf()) { index, value ->
                value.stringAt("$path.dependsOn[$index]")
            },
            parameters = (get("parameters") ?: JsonObject(emptyMap())).let {
                it.objectAt("$path.parameters")
            },
            contextBindings = optionalArray("contextBindings", path).mapIndexed { index, value ->
                val itemPath = "$path.contextBindings[$index]"
                value.objectAt(itemPath).toContextBinding(itemPath)
            },
            outputBindings = optionalArray("outputBindings", path).mapIndexed { index, value ->
                val itemPath = "$path.outputBindings[$index]"
                value.objectAt(itemPath).toOutputBinding(itemPath)
            },
            policy = (get("policy") ?: JsonObject(emptyMap())).objectAt("$path.policy").toPolicy("$path.policy")
        )
    }

    private fun JsonObject.toContextBinding(path: String): WorkflowIrContextBinding {
        rejectUnknown(CONTEXT_BINDING_FIELDS, path)
        return WorkflowIrContextBinding(
            parameterPath = requiredString("parameterPath", path),
            contextKey = requiredString("contextKey", path),
            required = optionalBoolean("required", path) ?: true
        )
    }

    private fun JsonObject.toOutputBinding(path: String): WorkflowIrOutputBinding {
        rejectUnknown(OUTPUT_BINDING_FIELDS, path)
        return WorkflowIrOutputBinding(
            sourceContextKey = requiredString("sourceContextKey", path),
            targetContextKey = requiredString("targetContextKey", path),
            required = optionalBoolean("required", path) ?: true
        )
    }

    private fun JsonObject.toPolicy(path: String): WorkflowIrStepPolicy {
        rejectUnknown(POLICY_FIELDS, path)
        val retryPath = "$path.retry"
        val retryObject = (get("retry") ?: JsonObject(emptyMap())).objectAt(retryPath)
        retryObject.rejectUnknown(RETRY_FIELDS, retryPath)
        return WorkflowIrStepPolicy(
            timeoutMs = optionalNullableLong("timeoutMs", path),
            retry = WorkflowIrRetryPolicy(
                maxAttempts = retryObject.optionalInt("maxAttempts", retryPath) ?: 1,
                initialDelayMs = retryObject.optionalLong("initialDelayMs", retryPath) ?: 1_000,
                backoffMultiplier = retryObject.optionalDouble("backoffMultiplier", retryPath) ?: 2.0,
                maxDelayMs = retryObject.optionalLong("maxDelayMs", retryPath) ?: 60_000
            ),
            failureStrategy = optionalString("failureStrategy", path)?.let {
                enumValues<WorkflowIrFailureStrategy>().firstOrNull { value -> value.name == it }
                    ?: fail("$path.failureStrategy", "unsupported failure strategy '$it'")
            } ?: WorkflowIrFailureStrategy.STOP_WORKFLOW
        )
    }

    companion object {
        private val ROOT_FIELDS = setOf("schemaVersion", "id", "version", "description", "inputs", "outputs", "steps")
        private val INPUT_FIELDS = setOf("name", "contextKey", "required", "description", "default")
        private val OUTPUT_FIELDS = setOf("name", "contextKey", "required", "description")
        private val STEP_FIELDS = setOf("id", "type", "version", "order", "dependsOn", "parameters", "contextBindings", "outputBindings", "policy")
        private val CONTEXT_BINDING_FIELDS = setOf("parameterPath", "contextKey", "required")
        private val OUTPUT_BINDING_FIELDS = setOf("sourceContextKey", "targetContextKey", "required")
        private val POLICY_FIELDS = setOf("timeoutMs", "retry", "failureStrategy")
        private val RETRY_FIELDS = setOf("maxAttempts", "initialDelayMs", "backoffMultiplier", "maxDelayMs")
    }
}

private fun WorkflowIr.toJson() = buildJsonObject {
    put("schemaVersion", schemaVersion); put("id", id); put("version", version)
    description?.let { put("description", it) }
    put("inputs", buildJsonArray { inputs.forEach { add(it.toJson()) } })
    put("outputs", buildJsonArray { outputs.forEach { add(it.toJson()) } })
    put("steps", buildJsonArray { steps.forEach { add(it.toJson()) } })
}

private fun WorkflowIrInput.toJson() = buildJsonObject {
    put("name", name); put("contextKey", contextKey); put("required", required)
    description?.let { put("description", it) }; defaultValue?.let { put("default", it) }
}

private fun WorkflowIrOutput.toJson() = buildJsonObject {
    put("name", name); put("contextKey", contextKey); put("required", required)
    description?.let { put("description", it) }
}

private fun WorkflowIrStep.toJson() = buildJsonObject {
    put("id", id); put("type", type); put("version", version); put("order", order)
    put("dependsOn", buildJsonArray { dependsOn.sorted().forEach { add(JsonPrimitive(it)) } })
    put("parameters", parameters)
    put("contextBindings", buildJsonArray { contextBindings.forEach { binding -> add(buildJsonObject {
        put("parameterPath", binding.parameterPath); put("contextKey", binding.contextKey); put("required", binding.required)
    }) } })
    put("outputBindings", buildJsonArray { outputBindings.forEach { binding -> add(buildJsonObject {
        put("sourceContextKey", binding.sourceContextKey); put("targetContextKey", binding.targetContextKey); put("required", binding.required)
    }) } })
    put("policy", buildJsonObject {
        policy.timeoutMs?.let { put("timeoutMs", it) }
        put("retry", buildJsonObject {
            put("maxAttempts", policy.retry.maxAttempts); put("initialDelayMs", policy.retry.initialDelayMs)
            put("backoffMultiplier", policy.retry.backoffMultiplier); put("maxDelayMs", policy.retry.maxDelayMs)
        })
        put("failureStrategy", policy.failureStrategy.name)
    })
}

private fun JsonObject.rejectUnknown(allowed: Set<String>, path: String) {
    (keys - allowed).sorted().firstOrNull()?.let { fail("$path.$it", "unknown property") }
}
private fun JsonObject.requiredString(name: String, path: String) = get(name)?.stringAt("$path.$name") ?: fail("$path.$name", "property is required")
private fun JsonObject.optionalString(name: String, path: String) = get(name)?.let { if (it === JsonNull) null else it.stringAt("$path.$name") }
private fun JsonObject.requiredInt(name: String, path: String) = get(name)?.integerAt("$path.$name")?.intOrNull ?: fail("$path.$name", "must be an integer")
private fun JsonObject.optionalInt(name: String, path: String) = get(name)?.let { it.integerAt("$path.$name").intOrNull ?: fail("$path.$name", "must be an integer") }
private fun JsonObject.optionalLong(name: String, path: String) = get(name)?.let { it.integerAt("$path.$name").longOrNull ?: fail("$path.$name", "must be an integer") }
private fun JsonObject.optionalNullableLong(name: String, path: String) = get(name)?.let { if (it === JsonNull) null else it.integerAt("$path.$name").longOrNull ?: fail("$path.$name", "must be an integer") }
private fun JsonObject.optionalDouble(name: String, path: String) = get(name)?.let { it.numberAt("$path.$name").doubleOrNull?.takeIf(Double::isFinite) ?: fail("$path.$name", "must be a finite number") }
private fun JsonObject.optionalBoolean(name: String, path: String) = get(name)?.let { (it as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.booleanOrNull ?: fail("$path.$name", "must be a boolean") }
private fun JsonObject.requiredArray(name: String, path: String) = get(name) as? JsonArray ?: fail("$path.$name", "must be an array")
private fun JsonObject.optionalArray(name: String, path: String) = get(name)?.let { it as? JsonArray ?: fail("$path.$name", "must be an array") } ?: JsonArray(emptyList())
private fun JsonElement.objectAt(path: String) = this as? JsonObject ?: fail(path, "must be an object")
private fun JsonElement.stringAt(path: String) = (this as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content ?: fail(path, "must be a string")
private fun JsonElement.numberAt(path: String) = (this as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString) ?: fail(path, "must be a number")
private fun JsonElement.integerAt(path: String) = (this as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString) ?: fail(path, "must be an integer")
private fun fail(path: String, message: String): Nothing = throw WorkflowIrJsonException("Invalid Workflow IR JSON at $path: $message.")
