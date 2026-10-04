package me.moiclathy.edb.core.task.workflow.schema

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionWorkflowStepHandler
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineExecutionStatus
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.SequentialPipelineExecutor
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIr
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCompiler
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrContextBinding
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrStep
import me.moiclathy.edb.core.task.workflow.ir.WorkflowStepParameterValidationException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkflowIrSchemaIntegrationTest {
    private val handler = object : WorkflowStepHandler {
        override val type = "example.positive"
        override suspend fun execute(step: WorkflowStepDefinition, context: PipelineContext) =
            PipelineStepResult.Success(context.withValue("validated", step.parameters.getValue("value")))
    }
    private val parameterSchema = WorkflowStepParameterSchema(handler.type, "1", buildJsonObject {
        put("\$schema", WorkflowJsonSchemaValidator.DIALECT)
        put("type", "object")
        put("properties", buildJsonObject {
            put("value", buildJsonObject { put("type", "integer"); put("minimum", 1) })
        })
        put("required", buildJsonArray { add(JsonPrimitive("value")) })
        put("additionalProperties", false)
    })

    @Test
    fun `defers bound values until runtime and validates their resolved type`() = runBlocking {
        val compiler = compiler()
        val workflow = WorkflowIr(
            id = "bound-schema",
            version = "1",
            steps = listOf(
                WorkflowIrStep(
                    "validate", handler.type, "1", 0,
                    contextBindings = listOf(WorkflowIrContextBinding("/value", "input.value"))
                )
            )
        )
        val pipeline = compiler.compile(workflow)

        val valid = SequentialPipelineExecutor().execute(pipeline, PipelineContext(mapOf("input.value" to 7)))
        val invalid = SequentialPipelineExecutor().execute(pipeline, PipelineContext(mapOf("input.value" to "seven")))

        assertEquals(PipelineExecutionStatus.COMPLETED, valid.status)
        assertEquals(PipelineExecutionStatus.FAILED, invalid.status)
        assertEquals("WORKFLOW_STEP_PARAMETERS_INVALID", invalid.failure?.code)
    }

    @Test
    fun `rejects invalid static parameters at compile time`() {
        val workflow = WorkflowIr(
            id = "static-schema",
            version = "1",
            steps = listOf(
                WorkflowIrStep(
                    "validate", handler.type, "1", 0,
                    parameters = buildJsonObject { put("value", 0); put("extra", true) }
                )
            )
        )

        val failure = assertFailsWith<WorkflowStepParameterValidationException> { compiler().compile(workflow) }
        assertEquals("validate", failure.stepId)
        assertEquals(setOf("minimum", "additionalProperties"), failure.violations.map { it.keyword }.toSet())
    }

    @Test
    fun `registers production schemas while preserving lower-case legacy formats`() {
        val registry = WorkflowStepSchemaRegistry(BuiltInWorkflowStepSchemas.all())
        val conversion = registry.require(DataFileConversionWorkflowStepHandler.TYPE, "1")
        val parameters = buildJsonObject { put("targetFormat", "csv"); put("sourceDataFileId", "12") }

        assertEquals(5, registry.registeredSchemas().size)
        assertTrue(WorkflowJsonSchemaValidator().validate(parameters, conversion.schema).isEmpty())
    }

    private fun compiler() = WorkflowIrCompiler(
        handlers = WorkflowStepHandlerRegistry(listOf(handler)),
        schemas = WorkflowStepSchemaRegistry(listOf(parameterSchema))
    )
}
