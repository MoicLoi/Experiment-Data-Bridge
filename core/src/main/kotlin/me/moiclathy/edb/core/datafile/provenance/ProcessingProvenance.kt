package me.moiclathy.edb.core.datafile.provenance

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.persistence.DataFileMetadataJsonCodec
import java.time.Instant

@Serializable
data class ProcessingProvenance(
    val schemaVersion: Int = 1,
    val operation: String,
    val parameterSnapshot: JsonObject,
    val inputRows: Int? = null,
    val outputRows: Int? = null,
    val affectedRows: Int? = null,
    val steps: List<JsonObject> = emptyList(),
    val handler: ProcessingHandlerIdentity,
    val randomSeed: Long? = null,
    val sourceDataFileIds: List<Int>,
    val outputDataFileId: Int,
    val taskId: Int,
    val userId: Int,
    val startedAt: String,
    val completedAt: String
)

@Serializable
data class ProcessingHandlerIdentity(val id: String, val tool: String, val version: String, val image: String? = null)

class ProcessingProvenanceCodec(private val json: Json = Json { prettyPrint = true; explicitNulls = false }) {
    fun encode(value: ProcessingProvenance): String = json.encodeToString(value)
    fun decode(value: String): ProcessingProvenance = json.decodeFromString(value)
}

object ProcessingProvenanceFactory {
    private val compactJson = Json { ignoreUnknownKeys = true }

    fun analysis(
        operation: String,
        parameters: Map<String, String>,
        metadata: DataFileMetadata,
        handlerId: String,
        handlerTool: String,
        handlerVersion: String,
        sourceIds: Set<Int>,
        outputId: Int,
        taskId: Int,
        userId: Int,
        startedAt: Instant,
        completedAt: Instant
    ): ProcessingProvenance {
        val metadataJson = compactJson.parseToJsonElement(DataFileMetadataJsonCodec().encode(metadata)).jsonObject
        val snapshot = buildJsonObject { parameters.toSortedMap().forEach { (key, value) -> put(key, parseOrString(value)) } }
        val spec = snapshot["spec"] as? JsonObject
        val configuredRules = (spec?.get("rules") ?: spec?.get("analyses"))?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
        val results = (metadataJson["rules"] ?: metadataJson["steps"])?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
        val steps = (0 until maxOf(configuredRules.size, results.size)).map { index ->
            buildJsonObject {
                (configuredRules.getOrNull(index) as? JsonObject)?.forEach { (key, value) -> put(key, value) }
                (results.getOrNull(index) as? JsonObject)?.forEach { (key, value) -> put(key, value) }
            }
        }
        return ProcessingProvenance(
            operation = operation,
            parameterSnapshot = snapshot,
            inputRows = metadataJson["inputRows"]?.jsonPrimitive?.intOrNull,
            outputRows = metadataJson["outputRows"]?.jsonPrimitive?.intOrNull,
            affectedRows = steps.sumOf { it["affectedRows"]?.jsonPrimitive?.intOrNull ?: 0 }.takeIf { steps.isNotEmpty() },
            steps = steps,
            handler = ProcessingHandlerIdentity(handlerId, handlerTool, metadataJson["workerVersion"]?.jsonPrimitive?.content ?: handlerVersion, metadataJson["imageVersion"]?.jsonPrimitive?.content),
            randomSeed = (metadataJson["randomSeed"] ?: spec?.get("randomSeed"))?.jsonPrimitive?.content?.toLongOrNull(),
            sourceDataFileIds = sourceIds.sorted(), outputDataFileId = outputId, taskId = taskId, userId = userId,
            startedAt = startedAt.toString(), completedAt = completedAt.toString()
        )
    }

    private fun parseOrString(value: String): JsonElement {
        val trimmed = value.trim()
        val looksJson = trimmed.startsWith('{') || trimmed.startsWith('[') ||
            trimmed in setOf("true", "false", "null") || trimmed.toBigDecimalOrNull() != null
        return if (looksJson) runCatching { compactJson.parseToJsonElement(trimmed) }.getOrElse { JsonPrimitive(value) }
        else JsonPrimitive(value)
    }
}
