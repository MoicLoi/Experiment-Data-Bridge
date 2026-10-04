package me.moiclathy.edb.core.task.workflow.application

import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.workflow.SetContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.ir.JsonWorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCompiler
import me.moiclathy.edb.core.task.workflow.schema.BuiltInWorkflowStepSchemas
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepSchemaRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkflowIrApplicationServiceTest {
    @Test
    fun `validation is side effect free and compile registers executable pipeline`() {
        val registry = PipelineRegistry()
        val handlers = WorkflowStepHandlerRegistry(listOf(SetContextWorkflowStepHandler()))
        val service = WorkflowIrApplicationService(
            JsonWorkflowIrCodec(),
            WorkflowIrCompiler(handlers, schemas = WorkflowStepSchemaRegistry(BuiltInWorkflowStepSchemas.all())),
            registry
        )

        val preview = service.validate(DOCUMENT)
        assertFalse(preview.registered)
        assertTrue(registry.registeredIds().isEmpty())

        val registered = service.compileAndRegister(DOCUMENT)
        assertTrue(registered.registered)
        assertEquals(listOf("first", "second"), registered.executionOrder)
        assertNotNull(registry.find("agent-flow"))
        assertFailsWith<WorkflowPipelineAlreadyRegisteredException> { service.compileAndRegister(DOCUMENT) }
    }

    companion object {
        private val DOCUMENT = """
            {"schemaVersion":1,"id":"agent-flow","version":"1","steps":[
              {"id":"second","type":"context.set","version":"1","order":10,"dependsOn":["first"],"parameters":{"key":"b","value":"2"}},
              {"id":"first","type":"context.set","version":"1","order":0,"parameters":{"key":"a","value":"1"}}
            ]}
        """.trimIndent()
    }
}
