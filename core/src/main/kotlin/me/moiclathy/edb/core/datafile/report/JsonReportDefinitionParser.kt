package me.moiclathy.edb.core.datafile.report

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import me.moiclathy.edb.core.datafile.model.DataFileFormat

class ReportDefinitionParseException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

fun interface ReportDefinitionParser {
    fun parse(value: String): ReportDefinition
}

class JsonReportDefinitionParser(
    private val json: Json = Json
) : ReportDefinitionParser {
    override fun parse(value: String): ReportDefinition {
        if (value.isBlank()) throw ReportDefinitionParseException("Report definition JSON must not be blank.")
        val root = try {
            json.parseToJsonElement(value) as? JsonObject
                ?: throw ReportDefinitionParseException("Report definition root must be an object.")
        } catch (failure: ReportDefinitionParseException) {
            throw failure
        } catch (failure: SerializationException) {
            throw ReportDefinitionParseException("Report definition JSON is malformed: ${failure.message}", failure)
        }
        root.rejectUnknown(ROOT_PROPERTIES)
        val schemaVersion = root.requiredInt("schemaVersion")
        if (schemaVersion != SCHEMA_VERSION) {
            throw ReportDefinitionParseException("Unsupported report definition schema version: $schemaVersion")
        }
        val outputFormatName = root.requiredString("outputFormat")
        val outputFormat = DataFileFormat.entries.firstOrNull { it.name == outputFormatName }
            ?: throw ReportDefinitionParseException("Unknown report output format: $outputFormatName")
        val parameters = root.optionalObject("defaultParameters")
            ?.mapValues { (key, parameter) -> parameter.requireString("$.defaultParameters.$key") }
            .orEmpty()
        return try {
            ReportDefinition(
                id = root.requiredString("id"),
                version = root.optionalInt("version") ?: 1,
                title = root.requiredString("title"),
                generatorId = root.requiredString("generatorId"),
                outputFormat = outputFormat,
                outputFileName = root.requiredString("outputFileName"),
                defaultParameters = parameters
            )
        } catch (failure: IllegalArgumentException) {
            throw ReportDefinitionParseException("Report definition is invalid: ${failure.message}", failure)
        }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        private val ROOT_PROPERTIES = setOf(
            "schemaVersion", "id", "version", "title", "generatorId", "outputFormat",
            "outputFileName", "defaultParameters"
        )
    }
}

private fun JsonObject.rejectUnknown(allowed: Set<String>) {
    val unknown = keys - allowed
    if (unknown.isNotEmpty()) {
        throw ReportDefinitionParseException("Unknown report definition properties: ${unknown.sorted().joinToString()}")
    }
}

private fun JsonObject.requiredString(name: String): String =
    this[name]?.requireString("$.$name")
        ?: throw ReportDefinitionParseException("Missing required report definition property: $.$name")

private fun JsonElement.requireString(path: String): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw ReportDefinitionParseException("Expected a string at $path.")

private fun JsonObject.requiredInt(name: String): Int = optionalInt(name)
    ?: throw ReportDefinitionParseException("Missing required integer property: $.$name")

private fun JsonObject.optionalInt(name: String): Int? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        ?: throw ReportDefinitionParseException("Expected an integer at $.$name.")
}

private fun JsonObject.optionalObject(name: String): JsonObject? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value as? JsonObject
        ?: throw ReportDefinitionParseException("Expected an object at $.$name.")
}
