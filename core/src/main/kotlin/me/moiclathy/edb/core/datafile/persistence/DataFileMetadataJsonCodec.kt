package me.moiclathy.edb.core.datafile.persistence

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue

class DataFileMetadataPersistenceException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class DataFileMetadataJsonCodec(
    private val json: Json = Json
) {
    fun encode(metadata: DataFileMetadata): String = json.encodeToString(
        JsonElement.serializer(),
        buildJsonObject { metadata.values.forEach { (key, value) -> put(key, value.toJson()) } }
    )

    fun decode(value: String): DataFileMetadata {
        val root = try {
            json.parseToJsonElement(value) as? JsonObject
                ?: throw DataFileMetadataPersistenceException("Persisted DataFile metadata must be an object.")
        } catch (failure: DataFileMetadataPersistenceException) {
            throw failure
        } catch (failure: SerializationException) {
            throw DataFileMetadataPersistenceException("Persisted DataFile metadata is malformed.", failure)
        }
        return try {
            DataFileMetadata(
                root.mapValues { (key, item) -> item.toMetadataValue("$.metadata.$key", 1) }
            )
        } catch (failure: DataFileMetadataPersistenceException) {
            throw failure
        } catch (failure: IllegalArgumentException) {
            throw DataFileMetadataPersistenceException("Persisted DataFile metadata is invalid.", failure)
        }
    }

    private fun JsonElement.toMetadataValue(path: String, depth: Int): DataFileMetadataValue {
        if (depth > MAX_DEPTH) {
            throw DataFileMetadataPersistenceException("Persisted metadata exceeds depth $MAX_DEPTH at $path.")
        }
        return when (this) {
            JsonNull -> DataFileMetadataValue.NullValue
            is JsonObject -> DataFileMetadataValue.ObjectValue(
                mapValues { (key, value) -> value.toMetadataValue("$path.$key", depth + 1) }
            )
            is JsonArray -> DataFileMetadataValue.ArrayValue(
                mapIndexed { index, value -> value.toMetadataValue("$path[$index]", depth + 1) }
            )
            is JsonPrimitive -> when {
                isString -> DataFileMetadataValue.StringValue(content)
                booleanOrNull != null -> DataFileMetadataValue.BooleanValue(booleanOrNull!!)
                else -> DataFileMetadataValue.NumberValue(
                    content.toBigDecimalOrNull()
                        ?: throw DataFileMetadataPersistenceException("Invalid metadata value at $path.")
                )
            }
        }
    }

    companion object {
        private const val MAX_DEPTH = 32
    }
}

private fun DataFileMetadataValue.toJson(): JsonElement = when (this) {
    is DataFileMetadataValue.StringValue -> JsonPrimitive(value)
    is DataFileMetadataValue.NumberValue -> JsonPrimitive(value)
    is DataFileMetadataValue.BooleanValue -> JsonPrimitive(value)
    is DataFileMetadataValue.ObjectValue -> buildJsonObject {
        values.forEach { (key, value) -> put(key, value.toJson()) }
    }
    is DataFileMetadataValue.ArrayValue -> buildJsonArray { values.forEach { add(it.toJson()) } }
    DataFileMetadataValue.NullValue -> JsonNull
}

