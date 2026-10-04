package me.moiclathy.edb.core.task.workflow.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

data class WorkflowJsonSchemaViolation(
    val instancePath: String,
    val schemaPath: String,
    val keyword: String,
    val message: String
)

class WorkflowJsonSchemaDefinitionException(message: String) : IllegalArgumentException(message)

/**
 * Deterministic JSON Schema 2020-12 subset for workflow step parameters.
 *
 * Supported assertions: type, enum, const, object properties/patternProperties/required/
 * additionalProperties/property counts, string length/pattern, numeric ranges/multipleOf,
 * array items/count/uniqueItems, allOf/anyOf/oneOf/not, and local JSON Pointer references.
 */
class WorkflowJsonSchemaValidator {
    fun validateSchemaDefinition(schema: JsonObject) {
        val dialect = schema["\$schema"]?.stringOrNull()
            ?: throw definition("Root schema must declare \$schema.")
        if (dialect != DIALECT) throw definition("Unsupported JSON Schema dialect: $dialect")
        validateSchemaNode(schema, schema, "#")
        val rootTypes = schema.types()
            ?: throw definition("Workflow step parameter schema root must declare type object.")
        if ("object" !in rootTypes) throw definition("Workflow step parameter schema root type must include object.")
    }

    fun validate(
        instance: JsonObject,
        schema: JsonObject,
        boundParameterPaths: Set<String> = emptySet()
    ): List<WorkflowJsonSchemaViolation> {
        val violations = mutableListOf<WorkflowJsonSchemaViolation>()
        validateNode(instance, schema, schema, "", "#", boundParameterPaths, violations)
        return violations
    }

    private fun validateNode(
        instance: JsonElement,
        schema: JsonObject,
        root: JsonObject,
        instancePath: String,
        schemaPath: String,
        boundPaths: Set<String>,
        violations: MutableList<WorkflowJsonSchemaViolation>
    ) {
        if (instancePath in boundPaths) return
        schema["\$ref"]?.stringOrNull()?.let { reference ->
            validateNode(instance, resolveReference(root, reference), root, instancePath, "$schemaPath/\$ref", boundPaths, violations)
            return
        }

        val allowedTypes = schema.types()
        if (allowedTypes != null && allowedTypes.none { instance.matchesType(it) }) {
            violations += violation(instancePath, schemaPath, "type", "Expected ${allowedTypes.joinToString(" or ")}.")
            return
        }
        schema["enum"]?.let { enum ->
            if (instance !in enum.jsonArray) violations += violation(instancePath, schemaPath, "enum", "Value is not in the allowed set.")
        }
        schema["const"]?.let { constant ->
            if (instance != constant) violations += violation(instancePath, schemaPath, "const", "Value does not equal the required constant.")
        }

        validateCombinators(instance, schema, root, instancePath, schemaPath, boundPaths, violations)
        when (instance) {
            is JsonObject -> validateObject(instance, schema, root, instancePath, schemaPath, boundPaths, violations)
            is JsonArray -> validateArray(instance, schema, root, instancePath, schemaPath, boundPaths, violations)
            is JsonPrimitive -> if (instance.isString) {
                validateString(instance.content, schema, instancePath, schemaPath, violations)
            } else {
                validateNumber(instance, schema, instancePath, schemaPath, violations)
            }
            JsonNull -> Unit
        }
    }

    private fun validateObject(
        instance: JsonObject,
        schema: JsonObject,
        root: JsonObject,
        path: String,
        schemaPath: String,
        boundPaths: Set<String>,
        violations: MutableList<WorkflowJsonSchemaViolation>
    ) {
        val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
        val patterns = (schema["patternProperties"] as? JsonObject).orEmpty()
            .map { (pattern, child) -> Regex(pattern) to child.jsonObject }
        val effectiveKeys = instance.keys + directBoundChildren(path, boundPaths)
        schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.forEach { required ->
            val child = path.child(required)
            if (required !in instance && boundPaths.none { it == child || it.startsWith("$child/") }) {
                violations += violation(path, schemaPath, "required", "Required property '$required' is missing.")
            }
        }
        instance.forEach { (name, value) ->
            val childPath = path.child(name)
            properties[name]?.jsonObject?.let {
                validateNode(value, it, root, childPath, "$schemaPath/properties/${name.pointerEscape()}", boundPaths, violations)
            }
            patterns.filter { (pattern, _) -> pattern.containsMatchIn(name) }.forEach { (_, childSchema) ->
                validateNode(value, childSchema, root, childPath, "$schemaPath/patternProperties", boundPaths, violations)
            }
        }
        val additional = effectiveKeys.filter { name ->
            name !in properties && patterns.none { (pattern, _) -> pattern.containsMatchIn(name) }
        }
        when (val additionalSchema = schema["additionalProperties"]) {
            is JsonPrimitive -> if (additionalSchema.booleanOrNull == false) additional.forEach { name ->
                violations += violation(path.child(name), schemaPath, "additionalProperties", "Unknown property '$name'.")
            }
            is JsonObject -> additional.filter { it in instance }.forEach { name ->
                validateNode(instance.getValue(name), additionalSchema, root, path.child(name), "$schemaPath/additionalProperties", boundPaths, violations)
            }
            else -> Unit
        }
        val size = effectiveKeys.size
        schema["minProperties"]?.jsonPrimitive?.intOrNull?.takeIf { size < it }?.let {
            violations += violation(path, schemaPath, "minProperties", "Object has fewer than $it properties.")
        }
        schema["maxProperties"]?.jsonPrimitive?.intOrNull?.takeIf { size > it }?.let {
            violations += violation(path, schemaPath, "maxProperties", "Object has more than $it properties.")
        }
    }

    private fun validateArray(
        instance: JsonArray,
        schema: JsonObject,
        root: JsonObject,
        path: String,
        schemaPath: String,
        boundPaths: Set<String>,
        violations: MutableList<WorkflowJsonSchemaViolation>
    ) {
        (schema["items"] as? JsonObject)?.let { itemSchema ->
            instance.forEachIndexed { index, item ->
                validateNode(item, itemSchema, root, "$path/$index", "$schemaPath/items", boundPaths, violations)
            }
        }
        schema["minItems"]?.jsonPrimitive?.intOrNull?.takeIf { instance.size < it }?.let {
            violations += violation(path, schemaPath, "minItems", "Array has fewer than $it items.")
        }
        schema["maxItems"]?.jsonPrimitive?.intOrNull?.takeIf { instance.size > it }?.let {
            violations += violation(path, schemaPath, "maxItems", "Array has more than $it items.")
        }
        if (schema["uniqueItems"]?.jsonPrimitive?.booleanOrNull == true && instance.distinct().size != instance.size) {
            violations += violation(path, schemaPath, "uniqueItems", "Array items must be unique.")
        }
    }

    private fun validateString(
        value: String,
        schema: JsonObject,
        path: String,
        schemaPath: String,
        violations: MutableList<WorkflowJsonSchemaViolation>
    ) {
        schema["minLength"]?.jsonPrimitive?.intOrNull?.takeIf { value.length < it }?.let {
            violations += violation(path, schemaPath, "minLength", "String is shorter than $it characters.")
        }
        schema["maxLength"]?.jsonPrimitive?.intOrNull?.takeIf { value.length > it }?.let {
            violations += violation(path, schemaPath, "maxLength", "String is longer than $it characters.")
        }
        schema["pattern"]?.stringOrNull()?.let { pattern ->
            if (!Regex(pattern).containsMatchIn(value)) violations += violation(path, schemaPath, "pattern", "String does not match '$pattern'.")
        }
    }

    private fun validateNumber(
        value: JsonPrimitive,
        schema: JsonObject,
        path: String,
        schemaPath: String,
        violations: MutableList<WorkflowJsonSchemaViolation>
    ) {
        val number = value.doubleOrNull ?: return
        fun check(keyword: String, invalid: (Double) -> Boolean, message: (Double) -> String) {
            schema[keyword]?.jsonPrimitive?.doubleOrNull?.let { limit ->
                if (invalid(limit)) violations += violation(path, schemaPath, keyword, message(limit))
            }
        }
        check("minimum", { number < it }) { "Number is less than $it." }
        check("maximum", { number > it }) { "Number is greater than $it." }
        check("exclusiveMinimum", { number <= it }) { "Number must be greater than $it." }
        check("exclusiveMaximum", { number >= it }) { "Number must be less than $it." }
        schema["multipleOf"]?.jsonPrimitive?.doubleOrNull?.let { divisor ->
            val quotient = number / divisor
            if (abs(quotient - kotlin.math.round(quotient)) > 1e-9) {
                violations += violation(path, schemaPath, "multipleOf", "Number is not a multiple of $divisor.")
            }
        }
    }

    private fun validateCombinators(
        instance: JsonElement,
        schema: JsonObject,
        root: JsonObject,
        path: String,
        schemaPath: String,
        boundPaths: Set<String>,
        violations: MutableList<WorkflowJsonSchemaViolation>
    ) {
        schema["allOf"]?.jsonArray?.forEachIndexed { index, child ->
            validateNode(instance, child.jsonObject, root, path, "$schemaPath/allOf/$index", boundPaths, violations)
        }
        listOf("anyOf", "oneOf").forEach { keyword ->
            schema[keyword]?.jsonArray?.let { branches ->
                val successes = branches.count { child ->
                    mutableListOf<WorkflowJsonSchemaViolation>().also {
                        validateNode(instance, child.jsonObject, root, path, "$schemaPath/$keyword", boundPaths, it)
                    }.isEmpty()
                }
                val valid = if (keyword == "anyOf") successes > 0 else successes == 1
                if (!valid) violations += violation(path, schemaPath, keyword, "Value must satisfy ${if (keyword == "anyOf") "at least one" else "exactly one"} branch.")
            }
        }
        (schema["not"] as? JsonObject)?.let { child ->
            val childViolations = mutableListOf<WorkflowJsonSchemaViolation>()
            validateNode(instance, child, root, path, "$schemaPath/not", boundPaths, childViolations)
            if (childViolations.isEmpty()) violations += violation(path, schemaPath, "not", "Value matches a forbidden schema.")
        }
    }

    private fun validateSchemaNode(schema: JsonObject, root: JsonObject, path: String) {
        val unknown = schema.keys - SUPPORTED_KEYWORDS
        if (unknown.isNotEmpty()) throw definition("Unsupported keyword(s) at $path: ${unknown.sorted().joinToString()}")
        schema.types()?.forEach { if (it !in JSON_TYPES) throw definition("Unsupported type '$it' at $path/type.") }
        schema["pattern"]?.stringOrNull()?.let { runCatching { Regex(it) }.getOrElse { failure -> throw definition("Invalid pattern at $path: ${failure.message}") } }
        schema["patternProperties"]?.requireObject("$path/patternProperties")?.forEach { (pattern, child) ->
            runCatching { Regex(pattern) }.getOrElse { failure -> throw definition("Invalid patternProperties pattern at $path: ${failure.message}") }
            validateSchemaNode(child.requireObject("$path/patternProperties/$pattern"), root, "$path/patternProperties/${pattern.pointerEscape()}")
        }
        schema["properties"]?.requireObject("$path/properties")?.forEach { (name, child) ->
            validateSchemaNode(child.requireObject("$path/properties/$name"), root, "$path/properties/${name.pointerEscape()}")
        }
        schema["\$defs"]?.requireObject("$path/\$defs")?.forEach { (name, child) ->
            validateSchemaNode(child.requireObject("$path/\$defs/$name"), root, "$path/\$defs/${name.pointerEscape()}")
        }
        listOf("allOf", "anyOf", "oneOf").forEach { keyword ->
            schema[keyword]?.requireArray("$path/$keyword")?.forEachIndexed { index, child ->
                validateSchemaNode(child.requireObject("$path/$keyword/$index"), root, "$path/$keyword/$index")
            }
        }
        schema["not"]?.requireObject("$path/not")?.let { validateSchemaNode(it, root, "$path/not") }
        schema["items"]?.requireObject("$path/items")?.let { validateSchemaNode(it, root, "$path/items") }
        (schema["additionalProperties"] as? JsonObject)?.let { validateSchemaNode(it, root, "$path/additionalProperties") }
        schema["required"]?.requireArray("$path/required")?.let { required ->
            val values = required.map { it.stringOrNull() ?: throw definition("Required entries must be strings at $path/required.") }
            if (values.distinct().size != values.size) throw definition("Required entries must be unique at $path/required.")
        }
        schema["enum"]?.requireArray("$path/enum")?.let { values ->
            if (values.isEmpty() || values.distinct().size != values.size) {
                throw definition("enum must contain unique values and must not be empty at $path.")
            }
        }
        schema["examples"]?.requireArray("$path/examples")
        schema["additionalProperties"]?.let {
            if (it !is JsonObject && (it as? JsonPrimitive)?.booleanOrNull == null) {
                throw definition("additionalProperties must be a boolean or schema at $path.")
            }
        }
        schema["\$ref"]?.stringOrNull()?.let { resolveReference(root, it) }
        listOf("minLength", "maxLength", "minItems", "maxItems", "minProperties", "maxProperties").forEach { keyword ->
            schema[keyword]?.jsonPrimitive?.intOrNull?.takeIf { it >= 0 }
                ?: if (keyword in schema) throw definition("$keyword must be a non-negative integer at $path.") else Unit
        }
        schema["multipleOf"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0.0 }
            ?: if ("multipleOf" in schema) throw definition("multipleOf must be positive at $path.") else Unit
        listOf("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum").forEach { keyword ->
            if (keyword in schema && schema[keyword]?.jsonPrimitive?.takeUnless { it.isString }?.doubleOrNull == null) {
                throw definition("$keyword must be a number at $path.")
            }
        }
        listOf("uniqueItems", "readOnly", "writeOnly", "deprecated").forEach { keyword ->
            if (keyword in schema && schema[keyword]?.jsonPrimitive?.booleanOrNull == null) {
                throw definition("$keyword must be a boolean at $path.")
            }
        }
        listOf("\$id", "title", "description", "format", "pattern").forEach { keyword ->
            if (keyword in schema && schema[keyword]?.stringOrNull() == null) {
                throw definition("$keyword must be a string at $path.")
            }
        }
        if ("\$ref" in schema && schema["\$ref"]?.stringOrNull() == null) {
            throw definition("\$ref must be a string at $path.")
        }
    }

    private fun resolveReference(root: JsonObject, reference: String): JsonObject {
        if (!reference.startsWith("#/")) throw definition("Only local JSON Pointer references are supported: $reference")
        var current: JsonElement = root
        reference.removePrefix("#/").split('/').forEach { token ->
            val name = token.replace("~1", "/").replace("~0", "~")
            current = (current as? JsonObject)?.get(name)
                ?: throw definition("Unresolved JSON Schema reference: $reference")
        }
        return current as? JsonObject ?: throw definition("JSON Schema reference is not an object: $reference")
    }

    private fun JsonObject.types(): Set<String>? = when (val type = this["type"]) {
        null -> null
        is JsonPrimitive -> setOf(type.stringOrNull() ?: throw definition("Schema type must be a string."))
        is JsonArray -> type.map { it.stringOrNull() ?: throw definition("Schema types must be strings.") }.toSet()
        else -> throw definition("Schema type must be a string or array of strings.")
    }

    private fun JsonElement.matchesType(type: String): Boolean = when (type) {
        "null" -> this is JsonNull
        "object" -> this is JsonObject
        "array" -> this is JsonArray
        "string" -> this is JsonPrimitive && isString
        "boolean" -> this is JsonPrimitive && !isString && booleanOrNull != null
        "number" -> this is JsonPrimitive && !isString && doubleOrNull != null
        "integer" -> this is JsonPrimitive && !isString && doubleOrNull?.let { it % 1.0 == 0.0 } == true
        else -> false
    }

    private fun directBoundChildren(path: String, bounds: Set<String>): Set<String> {
        val prefix = if (path.isEmpty()) "/" else "$path/"
        return bounds.mapNotNull { bound ->
            if (!bound.startsWith(prefix)) null else bound.removePrefix(prefix).substringBefore('/').pointerUnescape()
        }.toSet()
    }

    private fun String.child(name: String) = this + "/" + name.pointerEscape()
    private fun String.pointerEscape() = replace("~", "~0").replace("/", "~1")
    private fun String.pointerUnescape() = replace("~1", "/").replace("~0", "~")
    private fun JsonElement.stringOrNull() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement.requireObject(path: String) = this as? JsonObject ?: throw definition("Expected schema object at $path.")
    private fun JsonElement.requireArray(path: String) = this as? JsonArray ?: throw definition("Expected array at $path.")
    private fun violation(path: String, schemaPath: String, keyword: String, message: String) =
        WorkflowJsonSchemaViolation(path.ifEmpty { "/" }, schemaPath, keyword, message)
    private fun definition(message: String) = WorkflowJsonSchemaDefinitionException(message)

    companion object {
        const val DIALECT = "https://json-schema.org/draft/2020-12/schema"
        private val JSON_TYPES = setOf("null", "object", "array", "string", "boolean", "number", "integer")
        private val SUPPORTED_KEYWORDS = setOf(
            "\$schema", "\$id", "\$defs", "\$ref", "title", "description", "type", "format",
            "readOnly", "writeOnly", "deprecated", "properties", "patternProperties", "additionalProperties",
            "required", "enum", "const", "default", "examples", "minimum", "maximum", "exclusiveMinimum",
            "exclusiveMaximum", "multipleOf", "minLength", "maxLength", "pattern", "minItems", "maxItems",
            "uniqueItems", "items", "minProperties", "maxProperties", "allOf", "anyOf", "oneOf", "not"
        )
    }
}
