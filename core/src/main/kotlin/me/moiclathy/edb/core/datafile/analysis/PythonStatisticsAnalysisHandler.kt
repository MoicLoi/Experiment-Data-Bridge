package me.moiclathy.edb.core.datafile.analysis

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.task.workflow.schema.WorkflowJsonSchemaValidator
import java.net.URI
import java.time.Duration

class PythonStatisticsAnalysisHandler(
    id: String,
    analysisType: String,
    endpoint: URI,
    outputFormat: DataFileFormat,
    bearerToken: String?,
    connectTimeout: Duration,
    requestTimeout: Duration,
    private val json: Json = Json
) : ExternalHttpAnalysisHandler(
    ExternalHttpAnalysisHandlerConfig(
        id, analysisType, "python-http", endpoint, outputFormat, bearerToken,
        connectTimeout, requestTimeout, "PYTHON_STATISTICS"
    ), json
) {
    override val parameterContract = CONTRACT

    override fun encodeParameterValues(values: JsonObject): Map<String, String> {
        val violations = WorkflowJsonSchemaValidator().validate(values, parameterContract.schema)
        require(violations.isEmpty()) {
            violations.joinToString(prefix = "Invalid tabular-statistics parameters: ", separator = "; ") {
                "${it.instancePath} ${it.message}"
            }
        }
        val analyses = values["analyses"] as? JsonArray
            ?: throw IllegalArgumentException("Statistics analyses are required.")
        val spec = buildJsonObject {
            put("schemaVersion", 1)
            put("analyses", analyses)
        }
        return buildMap {
            put("operation", "tabular-statistics")
            put("spec", json.encodeToString(JsonObject.serializer(), spec))
            values["outputFileName"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf(String::isNotEmpty)?.let { put("outputFileName", it) }
        }
    }

    companion object {
        private val METHODS = listOf("welch_t_test", "mann_whitney_u", "fisher_exact", "spearman")
        val CONTRACT = AnalysisParameterContract(
            id = "tabular-statistics",
            version = 1,
            title = "Statistical analysis",
            description = "Build common statistical tests with a guided form; JSON remains available as an advanced mode.",
            schema = buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
                put("required", buildJsonArray { add(JsonPrimitive("analyses")) })
                put("properties", buildJsonObject {
                    put("outputFileName", buildJsonObject {
                        put("type", "string"); put("title", "Output file name"); put("default", "statistics.json")
                    })
                    put("analyses", buildJsonObject {
                        put("type", "array"); put("title", "Analyses"); put("minItems", 1)
                        put("items", buildJsonObject {
                            put("type", "object")
                            put("additionalProperties", true)
                            put("required", buildJsonArray {
                                listOf("name", "method").forEach { add(JsonPrimitive(it)) }
                            })
                            put("properties", buildJsonObject {
                                put("name", stringSchema("Analysis name"))
                                put("method", buildJsonObject {
                                    put("type", "string"); put("title", "Method")
                                    put("enum", buildJsonArray { METHODS.forEach { add(JsonPrimitive(it)) } })
                                })
                                listOf("groupColumn", "groupA", "groupB", "valueColumn", "rowColumn", "columnColumn", "xColumn", "yColumn").forEach {
                                    put(it, stringSchema(it))
                                }
                                put("rowValues", stringArraySchema("Row values"))
                                put("columnValues", stringArraySchema("Column values"))
                                put("alternative", buildJsonObject {
                                    put("type", "string"); put("enum", buildJsonArray { listOf("two-sided", "less", "greater").forEach { add(JsonPrimitive(it)) } }); put("default", "two-sided")
                                })
                                put("filters", buildJsonObject { put("type", "object"); put("additionalProperties", stringSchema("Filter value")) })
                            })
                        })
                    })
                })
                put("x-ui", buildJsonObject { put("order", buildJsonArray { add(JsonPrimitive("outputFileName")); add(JsonPrimitive("analyses")) }) })
            }
        )

        private fun stringSchema(title: String) = buildJsonObject { put("type", "string"); put("title", title); put("minLength", 1) }
        private fun stringArraySchema(title: String) = buildJsonObject {
            put("type", "array"); put("title", title); put("minItems", 2); put("maxItems", 2); put("items", stringSchema("Value"))
        }
    }
}
