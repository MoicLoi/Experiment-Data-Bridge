package me.moiclathy.edb.core.task.workflow

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

class WorkflowParseException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

fun interface JsonWorkflowParser {
    fun parse(json: String): WorkflowDefinition
}

/** Strict parser: unknown properties and non-string step configuration values are rejected. */
class KotlinxJsonWorkflowParser(
    private val json: Json = Json
) : JsonWorkflowParser {
    override fun parse(json: String): WorkflowDefinition {
        if (json.isBlank()) throw WorkflowParseException("Workflow JSON must not be blank.")

        val root = try {
            this.json.parseToJsonElement(json)
        } catch (failure: SerializationException) {
            throw WorkflowParseException("Workflow JSON is malformed: ${failure.message}", failure)
        }

        return parseDefinition(root.requireObject("$"))
    }

    private fun parseDefinition(root: JsonObject): WorkflowDefinition {
        root.rejectUnknown(setOf("schemaVersion", "id", "name", "steps"), "$")
        val stepsElement = root["steps"]
            ?: throw WorkflowParseException("Missing required property: $.steps")
        val steps = runCatching { stepsElement.requireArray("$.steps") }
            .getOrElse { throw it }
            .mapIndexed { index, element -> parseStep(element.requireObject("$.steps[$index]"), index) }

        return WorkflowDefinition(
            schemaVersion = root.requireInteger("schemaVersion", "$.schemaVersion"),
            id = root.requireString("id", "$.id"),
            name = root.optionalString("name", "$.name"),
            steps = steps
        )
    }

    private fun parseStep(value: JsonObject, index: Int): WorkflowStepDefinition {
        val path = "$.steps[$index]"
        value.rejectUnknown(setOf("id", "type", "config"), path)
        val config = value["config"]?.let { element ->
            element.requireObject("$path.config").mapValues { (key, configValue) ->
                configValue.requireString("$path.config.$key")
            }
        }.orEmpty()

        return WorkflowStepDefinition(
            id = value.requireString("id", "$path.id"),
            type = value.requireString("type", "$path.type"),
            config = config
        )
    }
}

private fun JsonElement.requireObject(path: String): JsonObject = this as? JsonObject
    ?: throw WorkflowParseException("Expected an object at $path.")

private fun JsonElement.requireArray(path: String) =
    (this as? kotlinx.serialization.json.JsonArray)
        ?: throw WorkflowParseException("Expected an array at $path.")

private fun JsonElement.requireString(path: String): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw WorkflowParseException("Expected a string at $path.")

private fun JsonObject.requireString(name: String, path: String): String =
    this[name]?.requireString(path)
        ?: throw WorkflowParseException("Missing required property: $path")

private fun JsonObject.optionalString(name: String, path: String): String? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.requireString(path)
}

private fun JsonObject.requireInteger(name: String, path: String): Int {
    val value = this[name]
        ?: throw WorkflowParseException("Missing required property: $path")
    return (value as? JsonPrimitive)
        ?.takeUnless { it.isString }
        ?.intOrNull
        ?: throw WorkflowParseException("Expected an integer at $path.")
}

private fun JsonObject.rejectUnknown(allowed: Set<String>, path: String) {
    val unknown = keys - allowed
    if (unknown.isNotEmpty()) {
        throw WorkflowParseException(
            "Unknown properties at $path: ${unknown.sorted().joinToString()}"
        )
    }
}

