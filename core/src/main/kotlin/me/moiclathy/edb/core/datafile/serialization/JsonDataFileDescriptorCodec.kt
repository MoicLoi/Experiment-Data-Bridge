package me.moiclathy.edb.core.datafile.serialization

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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.model.DataFileStorageReference
import java.time.Instant

class DataFileDescriptorJsonException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/** Strict, versioned JSON codec for DataFile exchange descriptors. */
class JsonDataFileDescriptorCodec(
    private val json: Json = Json { prettyPrint = true }
) : DataFileDescriptorCodec {
    override fun serialize(descriptor: DataFileDescriptor): String {
        validateModel(descriptor)
        return json.encodeToString(JsonElement.serializer(), descriptor.toJson())
    }

    override fun deserialize(value: String): DataFileDescriptor {
        if (value.isBlank()) throw DataFileDescriptorJsonException("DataFile descriptor JSON must not be blank.")
        val root = try {
            json.parseToJsonElement(value).requireObject("$")
        } catch (failure: DataFileDescriptorJsonException) {
            throw failure
        } catch (failure: SerializationException) {
            throw DataFileDescriptorJsonException(
                "DataFile descriptor JSON is malformed: ${failure.message}",
                failure
            )
        }

        val descriptor = parseDescriptor(root)
        validateModel(descriptor)
        return descriptor
    }

    private fun parseDescriptor(root: JsonObject): DataFileDescriptor {
        root.rejectUnknown(ROOT_PROPERTIES, "$")
        val version = root.requiredInt("schemaVersion", "$.schemaVersion")
        if (version != DataFileDescriptor.CURRENT_SCHEMA_VERSION) {
            throw DataFileDescriptorJsonException(
                "Unsupported DataFile descriptor schema version at $.schemaVersion: $version"
            )
        }
        return try {
            DataFileDescriptor(
                schemaVersion = version,
                id = root.optionalInt("id", "$.id"),
                projectId = root.requiredInt("projectId", "$.projectId"),
                createdByUserId = root.requiredInt("createdByUserId", "$.createdByUserId"),
                kind = root.requiredEnum("kind", "$.kind"),
                format = root.requiredEnum("format", "$.format"),
                status = root.requiredEnum("status", "$.status"),
                originalFileName = root.requiredString("originalFileName", "$.originalFileName"),
                mediaType = root.optionalString("mediaType", "$.mediaType"),
                sizeBytes = root.optionalLong("sizeBytes", "$.sizeBytes"),
                sha256 = root.optionalString("sha256", "$.sha256"),
                storage = root.optionalObject("storage", "$.storage")?.parseStorage(),
                provenance = root.requiredObject("provenance", "$.provenance").parseProvenance(),
                metadata = DataFileMetadata(
                    root.requiredObject("metadata", "$.metadata")
                        .mapValues { (key, metadataValue) ->
                            metadataValue.parseMetadata("$.metadata.$key", 1)
                        }
                ),
                createdAt = root.requiredInstant("createdAt", "$.createdAt"),
                updatedAt = root.requiredInstant("updatedAt", "$.updatedAt")
            )
        } catch (failure: DataFileDescriptorJsonException) {
            throw failure
        } catch (failure: IllegalArgumentException) {
            throw DataFileDescriptorJsonException(
                "DataFile descriptor is invalid: ${failure.message}",
                failure
            )
        }
    }

    private fun JsonObject.parseStorage(): DataFileStorageReference {
        rejectUnknown(setOf("provider", "key"), "$.storage")
        return DataFileStorageReference(
            provider = requiredString("provider", "$.storage.provider"),
            key = requiredString("key", "$.storage.key")
        )
    }

    private fun JsonObject.parseProvenance(): DataFileProvenance {
        rejectUnknown(PROVENANCE_PROPERTIES, "$.provenance")
        val parents = requiredArray("parentDataFileIds", "$.provenance.parentDataFileIds")
            .mapIndexed { index, value -> value.requireInt("$.provenance.parentDataFileIds[$index]") }
        if (parents.distinct().size != parents.size) {
            throw DataFileDescriptorJsonException(
                "Duplicate values are not allowed at $.provenance.parentDataFileIds."
            )
        }
        return DataFileProvenance(
            origin = requiredEnum("origin", "$.provenance.origin"),
            parentDataFileIds = parents.toSet(),
            producingTaskId = optionalInt("producingTaskId", "$.provenance.producingTaskId"),
            externalSystem = optionalString("externalSystem", "$.provenance.externalSystem")
        )
    }

    private fun JsonElement.parseMetadata(path: String, depth: Int): DataFileMetadataValue {
        if (depth > MAX_METADATA_DEPTH) {
            throw DataFileDescriptorJsonException("Metadata nesting exceeds $MAX_METADATA_DEPTH at $path.")
        }
        return when (this) {
            JsonNull -> DataFileMetadataValue.NullValue
            is JsonObject -> DataFileMetadataValue.ObjectValue(
                mapValues { (key, value) -> value.parseMetadata("$path.$key", depth + 1) }
            )
            is JsonArray -> DataFileMetadataValue.ArrayValue(
                mapIndexed { index, value -> value.parseMetadata("$path[$index]", depth + 1) }
            )
            is JsonPrimitive -> when {
                isString -> DataFileMetadataValue.StringValue(content)
                booleanOrNull != null -> DataFileMetadataValue.BooleanValue(booleanOrNull!!)
                else -> DataFileMetadataValue.NumberValue(
                    content.toBigDecimalOrNull()
                        ?: throw DataFileDescriptorJsonException("Expected a JSON value at $path.")
                )
            }
        }
    }

    private fun validateModel(descriptor: DataFileDescriptor) {
        try {
            descriptor.toDataFile()
        } catch (failure: IllegalArgumentException) {
            throw DataFileDescriptorJsonException(
                "DataFile descriptor is invalid: ${failure.message}",
                failure
            )
        }
    }

    companion object {
        private const val MAX_METADATA_DEPTH = 32
        private val ROOT_PROPERTIES = setOf(
            "schemaVersion", "id", "projectId", "createdByUserId", "kind", "format", "status",
            "originalFileName", "mediaType", "sizeBytes", "sha256", "storage",
            "provenance", "metadata", "createdAt", "updatedAt"
        )
        private val PROVENANCE_PROPERTIES = setOf(
            "origin", "parentDataFileIds", "producingTaskId", "externalSystem"
        )
    }
}

private fun DataFileDescriptor.toJson(): JsonObject = buildJsonObject {
    put("schemaVersion", schemaVersion)
    putNullable("id", id)
    put("projectId", projectId)
    put("createdByUserId", createdByUserId)
    put("kind", kind.name)
    put("format", format.name)
    put("status", status.name)
    put("originalFileName", originalFileName)
    putNullable("mediaType", mediaType)
    putNullable("sizeBytes", sizeBytes)
    putNullable("sha256", sha256)
    put("storage", storage?.toJson() ?: JsonNull)
    put("provenance", provenance.toJson())
    put("metadata", metadata.toJson())
    put("createdAt", createdAt.toString())
    put("updatedAt", updatedAt.toString())
}

private fun DataFileStorageReference.toJson() = buildJsonObject {
    put("provider", provider)
    put("key", key)
}

private fun DataFileProvenance.toJson() = buildJsonObject {
    put("origin", origin.name)
    put(
        "parentDataFileIds",
        buildJsonArray { parentDataFileIds.sorted().forEach { add(JsonPrimitive(it)) } }
    )
    putNullable("producingTaskId", producingTaskId)
    putNullable("externalSystem", externalSystem)
}

private fun DataFileMetadata.toJson() = buildJsonObject {
    values.forEach { (key, value) -> put(key, value.toJson()) }
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

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: String?) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: Int?) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: Long?) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}

private fun JsonElement.requireObject(path: String): JsonObject = this as? JsonObject
    ?: throw DataFileDescriptorJsonException("Expected an object at $path.")

private fun JsonElement.requireInt(path: String): Int =
    (this as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        ?: throw DataFileDescriptorJsonException("Expected an integer at $path.")

private fun JsonObject.requiredObject(name: String, path: String): JsonObject =
    this[name]?.requireObject(path)
        ?: throw DataFileDescriptorJsonException("Missing required property: $path")

private fun JsonObject.optionalObject(name: String, path: String): JsonObject? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.requireObject(path)
}

private fun JsonObject.requiredArray(name: String, path: String): JsonArray =
    (this[name] ?: throw DataFileDescriptorJsonException("Missing required property: $path")) as? JsonArray
        ?: throw DataFileDescriptorJsonException("Expected an array at $path.")

private fun JsonObject.requiredString(name: String, path: String): String =
    (this[name] ?: throw DataFileDescriptorJsonException("Missing required property: $path"))
        .requireString(path)

private fun JsonElement.requireString(path: String): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw DataFileDescriptorJsonException("Expected a string at $path.")

private fun JsonObject.optionalString(name: String, path: String): String? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.requireString(path)
}

private fun JsonObject.requiredInt(name: String, path: String): Int =
    (this[name] ?: throw DataFileDescriptorJsonException("Missing required property: $path"))
        .requireInt(path)

private fun JsonObject.optionalInt(name: String, path: String): Int? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.requireInt(path)
}

private fun JsonObject.optionalLong(name: String, path: String): Long? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return (value as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        ?: throw DataFileDescriptorJsonException("Expected an integer at $path.")
}

private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(name: String, path: String): T {
    val value = requiredString(name, path)
    return enumValues<T>().firstOrNull { it.name == value }
        ?: throw DataFileDescriptorJsonException(
            "Unknown ${T::class.simpleName} at $path: $value"
        )
}

private fun JsonObject.requiredInstant(name: String, path: String): Instant {
    val value = requiredString(name, path)
    return try {
        Instant.parse(value)
    } catch (failure: Exception) {
        throw DataFileDescriptorJsonException("Expected an ISO-8601 instant at $path: $value", failure)
    }
}

private fun JsonObject.rejectUnknown(allowed: Set<String>, path: String) {
    val unknown = keys - allowed
    if (unknown.isNotEmpty()) {
        throw DataFileDescriptorJsonException(
            "Unknown properties at $path: ${unknown.sorted().joinToString()}"
        )
    }
}
