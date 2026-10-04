package me.moiclathy.edb.core.datafile.analysis

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.persistence.DataFileMetadataJsonCodec
import me.moiclathy.edb.core.task.workflow.schema.WorkflowJsonSchemaValidator
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

data class PythonHttpAnalysisHandlerConfig(
    val id: String = "python-http",
    val analysisType: String = "python",
    val endpoint: URI,
    val outputFormat: DataFileFormat = DataFileFormat.JSON,
    val bearerToken: String? = null,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val requestTimeout: Duration = Duration.ofMinutes(5)
) {
    internal fun external() = ExternalHttpAnalysisHandlerConfig(
        id = id,
        analysisType = analysisType,
        tool = "python-http",
        endpoint = endpoint,
        outputFormat = outputFormat,
        bearerToken = bearerToken,
        connectTimeout = connectTimeout,
        requestTimeout = requestTimeout,
        errorCodePrefix = "PYTHON"
    )
}

class PythonHttpAnalysisHandler(
    config: PythonHttpAnalysisHandlerConfig,
    private val parameterJson: Json = Json,
    metadataCodec: DataFileMetadataJsonCodec = DataFileMetadataJsonCodec(parameterJson),
    client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(config.connectTimeout)
        .version(HttpClient.Version.HTTP_1_1)
        .build()
) : ExternalHttpAnalysisHandler(config.external(), parameterJson, metadataCodec, client) {
    override val parameterContract: AnalysisParameterContract = TABULAR_CLEAN_CONTRACT

    override fun encodeParameterValues(values: JsonObject): Map<String, String> {
        val violations = WorkflowJsonSchemaValidator().validate(values, parameterContract.schema)
        require(violations.isEmpty()) {
            violations.joinToString(prefix = "Invalid tabular-clean parameters: ", separator = "; ") {
                "${it.instancePath.ifEmpty { "/" }} ${it.message}"
            }
        }
        val allowed = TABULAR_FIELDS
        val unknown = values.keys - allowed
        require(unknown.isEmpty()) { "Unknown tabular-clean parameters: ${unknown.sorted()}" }

        fun string(name: String, default: String = "") =
            values[name]?.jsonPrimitive?.contentOrNull ?: default
        fun bool(name: String, default: Boolean) =
            values[name]?.jsonPrimitive?.booleanOrNull ?: default
        fun columns(name: String, default: String) = string(name, default)
            .split(',').map(String::trim).filter(String::isNotEmpty).distinct()

        val outputFormat = string("outputFormat", "XLSX").uppercase()
        require(outputFormat in setOf("CSV", "JSON", "XLSX")) { "Unsupported tabular-clean output format: $outputFormat" }
        val normalizeCase = string("normalizeCase", "lower")
        require(normalizeCase in setOf("none", "lower", "upper", "title")) { "Invalid normalizeCase value." }
        val numericColumn = string("numericColumn", "measurement_mm").trim()
        val numericMin = values["numericMin"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val numericMax = values["numericMax"]?.jsonPrimitive?.doubleOrNull ?: 30.0
        require(numericMin <= numericMax) { "Numeric minimum must not exceed maximum." }

        val rules = buildJsonArray {
            if (bool("trimStrings", true)) add(buildJsonObject { put("type", "trimStrings") })
            val normalizeColumns = columns("normalizeColumns", "subject,group,quality_flag")
            if (normalizeCase != "none" && normalizeColumns.isNotEmpty()) add(buildJsonObject {
                put("type", "normalizeCase")
                put("columns", buildJsonArray { normalizeColumns.forEach { add(JsonPrimitive(it)) } })
                put("mode", normalizeCase)
            })
            val missingColumns = columns("dropMissingColumns", "measurement_mm")
            if (missingColumns.isNotEmpty()) add(buildJsonObject {
                put("type", "dropRowsWithMissing")
                put("columns", buildJsonArray { missingColumns.forEach { add(JsonPrimitive(it)) } })
            })
            val duplicateColumns = columns("duplicateKeyColumns", "sample_id")
            if (duplicateColumns.isNotEmpty()) add(buildJsonObject {
                put("type", "removeDuplicates")
                put("columns", buildJsonArray { duplicateColumns.forEach { add(JsonPrimitive(it)) } })
            })
            if (numericColumn.isNotEmpty()) add(buildJsonObject {
                put("type", "numericRange")
                put("column", numericColumn)
                put("min", numericMin)
                put("max", numericMax)
            })
        }
        val spec = buildJsonObject {
            put("schemaVersion", 1)
            put("identifierColumns", buildJsonArray { columns("identifierColumns", "").forEach { add(JsonPrimitive(it)) } })
            put("output", buildJsonObject {
                put("format", outputFormat)
                string("outputFileName").trim().takeIf(String::isNotEmpty)?.let { put("fileName", it) }
            })
            put("rules", rules)
        }
        return mapOf("operation" to "tabular-clean", "spec" to parameterJson.encodeToString(JsonObject.serializer(), spec))
    }

    companion object {
        const val FILE_NAME_HEADER = ExternalHttpAnalysisHandler.FILE_NAME_HEADER
        const val METADATA_HEADER = ExternalHttpAnalysisHandler.METADATA_HEADER

        private val TABULAR_FIELDS = setOf(
            "outputFormat", "outputFileName", "trimStrings", "normalizeCase", "normalizeColumns",
            "dropMissingColumns", "duplicateKeyColumns", "identifierColumns", "numericColumn", "numericMin", "numericMax"
        )
        val TABULAR_CLEAN_CONTRACT = AnalysisParameterContract(
            id = "tabular-clean",
            version = 1,
            title = "Tabular data cleaning",
            description = "Clean CSV, JSON, or XLSX data with a validated rule chain.",
            schema = buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
                put("properties", buildJsonObject {
                    put("outputFormat", enumField("Output format", listOf("XLSX", "CSV", "JSON"), "XLSX"))
                    put("outputFileName", stringField("Output file name", "Optional; the correct extension is required."))
                    put("trimStrings", booleanField("Trim surrounding whitespace", true))
                    put("normalizeCase", enumField("Text normalization", listOf("none", "lower", "upper", "title"), "lower"))
                    put("normalizeColumns", stringField("Columns to normalize", "Comma-separated column names.", "subject,group,quality_flag"))
                    put("dropMissingColumns", stringField("Required-value columns", "Rows missing one of these columns are removed.", "measurement_mm"))
                    put("duplicateKeyColumns", stringField("Duplicate key columns", "Keep the first row for each comma-separated key.", "sample_id"))
                    put("identifierColumns", stringField("Stable row identifier columns", "Optional comma-separated identifiers included in the provenance report.", "sample_id"))
                    put("numericColumn", stringField("Numeric range column", "Leave empty to disable range filtering.", "measurement_mm"))
                    put("numericMin", numberField("Minimum value", 0.0))
                    put("numericMax", numberField("Maximum value", 30.0))
                })
                put("x-ui", buildJsonObject {
                    put("order", buildJsonArray { TABULAR_FIELDS.forEach { add(JsonPrimitive(it)) } })
                })
            }
        )

        private fun stringField(title: String, description: String, default: String? = null) = buildJsonObject {
            put("type", "string"); put("title", title); put("description", description)
            default?.let { put("default", it) }
        }
        private fun booleanField(title: String, default: Boolean) = buildJsonObject {
            put("type", "boolean"); put("title", title); put("default", default)
        }
        private fun numberField(title: String, default: Double) = buildJsonObject {
            put("type", "number"); put("title", title); put("default", default)
        }
        private fun enumField(title: String, values: List<String>, default: String) = buildJsonObject {
            put("type", "string"); put("title", title); put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }); put("default", default)
        }
    }
}
