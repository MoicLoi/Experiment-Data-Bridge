package me.moiclathy.edb.web.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

fun kotlinxJsonJacksonModule(): SimpleModule = SimpleModule("kotlinx-json-elements").apply {
    val serializer = KotlinxJsonElementSerializer()
    addSerializer(JsonElement::class.java, serializer)
    addSerializer(JsonObject::class.java, serializer)
    addSerializer(JsonArray::class.java, serializer)
    addSerializer(JsonPrimitive::class.java, serializer)
    addDeserializer(JsonElement::class.java, KotlinxJsonElementDeserializer())
    addDeserializer(JsonObject::class.java, KotlinxJsonObjectDeserializer())
}

private class KotlinxJsonElementSerializer : JsonSerializer<JsonElement>() {
    override fun serialize(value: JsonElement, generator: JsonGenerator, serializers: SerializerProvider) {
        write(value, generator)
    }

    private fun write(value: JsonElement, generator: JsonGenerator) {
        when (value) {
            JsonNull -> generator.writeNull()
            is JsonObject -> {
                generator.writeStartObject()
                value.forEach { (name, item) -> generator.writeFieldName(name); write(item, generator) }
                generator.writeEndObject()
            }
            is JsonArray -> {
                generator.writeStartArray()
                value.forEach { write(it, generator) }
                generator.writeEndArray()
            }
            is JsonPrimitive -> when {
                value.isString -> generator.writeString(value.content)
                value.booleanOrNull != null -> generator.writeBoolean(requireNotNull(value.booleanOrNull))
                value.longOrNull != null -> generator.writeNumber(requireNotNull(value.longOrNull))
                value.doubleOrNull != null -> generator.writeNumber(requireNotNull(value.doubleOrNull))
                else -> generator.writeString(value.content)
            }
        }
    }
}

private open class KotlinxJsonElementDeserializer : JsonDeserializer<JsonElement>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): JsonElement =
        parser.codec.readTree<JsonNode>(parser).toKotlinxJson()
}

private class KotlinxJsonObjectDeserializer : JsonDeserializer<JsonObject>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): JsonObject =
        parser.codec.readTree<JsonNode>(parser).toKotlinxJson() as? JsonObject
            ?: context.handleUnexpectedToken(JsonObject::class.java, parser) as JsonObject
}

private fun JsonNode.toKotlinxJson(): JsonElement = when {
    isObject -> JsonObject(fields().asSequence().associate { it.key to it.value.toKotlinxJson() })
    isArray -> JsonArray(elements().asSequence().map { it.toKotlinxJson() }.toList())
    isNull -> JsonNull
    isTextual -> JsonPrimitive(textValue())
    isBoolean -> JsonPrimitive(booleanValue())
    isIntegralNumber -> JsonPrimitive(longValue())
    isFloatingPointNumber -> JsonPrimitive(doubleValue())
    else -> JsonPrimitive(asText())
}
