package me.moiclathy.edb.core.task.workflow.schema

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkflowJsonSchemaValidatorTest {
    private val validator = WorkflowJsonSchemaValidator()

    @Test
    fun `validates references objects arrays patterns and additional properties`() {
        val schema = buildJsonObject {
            put("\$schema", WorkflowJsonSchemaValidator.DIALECT)
            put("type", "object")
            put("\$defs", buildJsonObject {
                put("name", buildJsonObject { put("type", "string"); put("minLength", 1); put("pattern", "\\S") })
            })
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("\$ref", "#/\$defs/name") })
                put("tags", buildJsonObject {
                    put("type", "array")
                    put("uniqueItems", true)
                    put("items", buildJsonObject { put("type", "string") })
                })
            })
            put("patternProperties", buildJsonObject {
                put("^option\\.", buildJsonObject { put("type", "string") })
            })
            put("required", buildJsonArray { add(JsonPrimitive("name")) })
            put("additionalProperties", false)
        }
        validator.validateSchemaDefinition(schema)

        val valid = buildJsonObject {
            put("name", "summary")
            put("tags", buildJsonArray { add(JsonPrimitive("a")); add(JsonPrimitive("b")) })
            put("option.title", "Experiment")
        }
        assertTrue(validator.validate(valid, schema).isEmpty())

        val invalid = buildJsonObject {
            put("name", " ")
            put("tags", buildJsonArray { add(JsonPrimitive("a")); add(JsonPrimitive("a")) })
            put("unknown", true)
        }
        val keywords = validator.validate(invalid, schema).map { it.keyword }.toSet()
        assertEquals(setOf("pattern", "uniqueItems", "additionalProperties"), keywords)
    }

    @Test
    fun `registry rejects malformed and duplicate schemas`() {
        val valid = WorkflowStepParameterSchema("example", "1", buildJsonObject {
            put("\$schema", WorkflowJsonSchemaValidator.DIALECT)
            put("type", "object")
        })
        val registry = WorkflowStepSchemaRegistry(listOf(valid), validator)

        assertEquals(valid, registry.require("example", "1"))
        assertFailsWith<WorkflowStepSchemaRegistrationException> { registry.register(valid) }
        assertFailsWith<WorkflowJsonSchemaDefinitionException> {
            registry.register(WorkflowStepParameterSchema("invalid", "1", buildJsonObject {
                put("\$schema", WorkflowJsonSchemaValidator.DIALECT)
                put("type", "object")
                put("unsupportedKeyword", true)
            }))
        }
    }
}
