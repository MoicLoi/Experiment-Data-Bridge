package me.moiclathy.edb.core.task.workflow

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.task.pipeline.PipelineExecutionStatus
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.pipeline.SequentialPipelineExecutor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ContextWorkflowStepHandlersTest {
    @Test
    fun `production context handlers set and copy values and report invalid configuration`() = runBlocking {
        val handlers = WorkflowStepHandlerRegistry(
            listOf(SetContextWorkflowStepHandler(), CopyContextWorkflowStepHandler())
        )
        val driver = JsonWorkflowDriver(KotlinxJsonWorkflowParser(), WorkflowCompiler(handlers))
        val successful = driver.compile(
            """
            {
              "schemaVersion": 1,
              "id": "context-flow",
              "steps": [
                { "id": "set", "type": "context.set", "config": { "key": "source", "value": "raw.csv" } },
                { "id": "copy", "type": "context.copy", "config": { "from": "source", "to": "result" } }
              ]
            }
            """.trimIndent()
        )
        val executor = SequentialPipelineExecutor()

        val success = executor.execute(successful)

        assertEquals(PipelineExecutionStatus.COMPLETED, success.status)
        assertEquals("raw.csv", success.context.getAs<String>("result"))

        val invalid = driver.compile(
            """{"schemaVersion":1,"id":"invalid","steps":[{"id":"copy","type":"context.copy","config":{"from":"missing","to":"result"}}]}"""
        )
        val failure = executor.execute(invalid)
        assertEquals(PipelineExecutionStatus.FAILED, failure.status)
        assertEquals("WORKFLOW_CONTEXT_VALUE_MISSING", failure.failure?.code)
    }
}
