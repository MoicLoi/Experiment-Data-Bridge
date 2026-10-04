package me.moiclathy.edb.core.task.workflow.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.analysis.AnalysisWorkflowStepHandler
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionWorkflowStepHandler
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.report.ReportWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.CopyContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.SetContextWorkflowStepHandler

object BuiltInWorkflowStepSchemas {
    fun all(): List<WorkflowStepParameterSchema> = listOf(
        schema(
            SetContextWorkflowStepHandler.TYPE,
            "Set a string value in Pipeline context.",
            required = listOf("key", "value"),
            properties = mapOf("key" to nonBlankString(), "value" to stringSchema())
        ),
        schema(
            CopyContextWorkflowStepHandler.TYPE,
            "Copy one Pipeline context value to another key.",
            required = listOf("from", "to"),
            properties = mapOf("from" to nonBlankString(), "to" to nonBlankString())
        ),
        schema(
            DataFileConversionWorkflowStepHandler.TYPE,
            "Convert one DataFile and publish the derived DataFile id.",
            required = listOf("targetFormat"),
            properties = mapOf(
                "targetFormat" to caseInsensitiveEnumString(DataFileFormat.entries.map { it.name }),
                "sourceDataFileId" to positiveId(),
                "sourceDataFileIdContext" to nonBlankString(),
                "resultContextKey" to nonBlankString()
            ),
            patterns = mapOf(
                "^option\\..+$" to stringSchema(),
                "^optionContext\\..+$" to nonBlankString()
            ),
            allOf = listOf(
                anyRequired("sourceDataFileId", "sourceDataFileIdContext"),
                buildJsonObject {
                    put("not", buildJsonObject {
                        put("required", JsonArray(listOf(JsonPrimitive("sourceDataFileId"), JsonPrimitive("sourceDataFileIdContext"))))
                    })
                }
            )
        ),
        schema(
            AnalysisWorkflowStepHandler.TYPE,
            "Run a registered analysis Handler and publish its artifact id.",
            required = listOf("handlerId"),
            properties = mapOf(
                "handlerId" to nonBlankString(),
                "sourceDataFileIds" to positiveIdListString(),
                "sourceDataFileIdsContext" to nonBlankString(),
                "resultContextKey" to nonBlankString()
            ),
            patterns = mapOf(
                "^parameter\\..+$" to stringSchema(),
                "^parameterContext\\..+$" to nonBlankString()
            ),
            allOf = listOf(anyRequired("sourceDataFileIds", "sourceDataFileIdsContext"))
        ),
        schema(
            ReportWorkflowStepHandler.TYPE,
            "Generate a report artifact and publish its DataFile id.",
            required = listOf("definitionId"),
            properties = mapOf(
                "definitionId" to nonBlankString(),
                "sourceDataFileIds" to positiveIdListString(allowBlank = true),
                "sourceDataFileIdsContext" to nonBlankString(),
                "resultContextKey" to nonBlankString()
            ),
            patterns = mapOf(
                "^parameter\\..+$" to stringSchema(),
                "^parameterContext\\..+$" to nonBlankString()
            )
        )
    )

    private fun schema(
        type: String,
        description: String,
        required: List<String>,
        properties: Map<String, JsonObject>,
        patterns: Map<String, JsonObject> = emptyMap(),
        allOf: List<JsonObject> = emptyList()
    ) = WorkflowStepParameterSchema(
        type = type,
        version = "1",
        schema = buildJsonObject {
            put("\$schema", WorkflowJsonSchemaValidator.DIALECT)
            put("title", type)
            put("description", description)
            put("type", "object")
            put("properties", JsonObject(properties))
            if (patterns.isNotEmpty()) put("patternProperties", JsonObject(patterns))
            put("additionalProperties", false)
            if (required.isNotEmpty()) put("required", JsonArray(required.map(::JsonPrimitive)))
            if (allOf.isNotEmpty()) put("allOf", JsonArray(allOf))
        }
    )

    private fun stringSchema() = buildJsonObject { put("type", "string") }

    private fun nonBlankString() = buildJsonObject {
        put("type", "string")
        put("minLength", 1)
        put("pattern", "\\S")
    }

    private fun positiveId() = buildJsonObject {
        put("anyOf", buildJsonArray {
            add(buildJsonObject { put("type", "string"); put("pattern", "^[1-9][0-9]*$") })
            add(buildJsonObject { put("type", "integer"); put("minimum", 1) })
        })
    }

    private fun positiveIdListString(allowBlank: Boolean = false) = buildJsonObject {
        put("type", "string")
        put("pattern", if (allowBlank) "^(|[ ]*[1-9][0-9]*([ ]*,[ ]*[1-9][0-9]*)*[ ]*)$" else "^[ ]*[1-9][0-9]*([ ]*,[ ]*[1-9][0-9]*)*[ ]*$")
    }

    private fun caseInsensitiveEnumString(values: List<String>) = buildJsonObject {
        put("type", "string")
        val alternatives = values.joinToString("|") { value ->
            value.map { character ->
                if (character.isLetter()) "[${character.lowercaseChar()}${character.uppercaseChar()}]"
                else Regex.escape(character.toString())
            }.joinToString("")
        }
        put("pattern", "^($alternatives)$")
        put("examples", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }

    private fun anyRequired(vararg names: String) = buildJsonObject {
        put("anyOf", buildJsonArray {
            names.forEach { name ->
                add(buildJsonObject { put("required", JsonArray(listOf(JsonPrimitive(name)))) })
            }
        })
    }
}
