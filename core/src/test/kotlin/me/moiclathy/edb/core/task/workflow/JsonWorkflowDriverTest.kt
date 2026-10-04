package me.moiclathy.edb.core.task.workflow

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineExecutionStatus
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.SequentialPipelineExecutor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonWorkflowDriverTest {
    @Test
    fun `parses compiles and executes JSON workflow through registered handlers`() = runBlocking {
        val registry = WorkflowStepHandlerRegistry(
            listOf(
                object : WorkflowStepHandler {
                    override val type = "context.put"

                    override suspend fun execute(
                        step: WorkflowStepDefinition,
                        context: PipelineContext
                    ): PipelineStepResult.Success = PipelineStepResult.Success(
                        context.withValue(
                            requireNotNull(step.config["key"]),
                            requireNotNull(step.config["value"])
                        )
                    )
                },
                object : WorkflowStepHandler {
                    override val type = "context.copy"

                    override suspend fun execute(
                        step: WorkflowStepDefinition,
                        context: PipelineContext
                    ): PipelineStepResult.Success = PipelineStepResult.Success(
                        context.withValue(
                            requireNotNull(step.config["to"]),
                            context[requireNotNull(step.config["from"])]
                        )
                    )
                }
            )
        )
        val driver = JsonWorkflowDriver(
            KotlinxJsonWorkflowParser(),
            WorkflowCompiler(registry)
        )

        val pipeline = driver.compile(
            """
            {
              "schemaVersion": 1,
              "id": "research-data-processing",
              "name": "Research data processing",
              "steps": [
                {
                  "id": "load",
                  "type": "context.put",
                  "config": { "key": "source", "value": "raw.csv" }
                },
                {
                  "id": "copy",
                  "type": "context.copy",
                  "config": { "from": "source", "to": "result" }
                }
              ]
            }
            """.trimIndent()
        )
        val result = SequentialPipelineExecutor().execute(pipeline)

        assertEquals(PipelineExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf("load", "copy"), result.completedStepIds)
        assertEquals("raw.csv", result.context.getAs<String>("result"))
    }

    @Test
    fun `rejects malformed definitions and unknown step types before execution`() {
        val parser = KotlinxJsonWorkflowParser()
        val nonStringConfig = assertFailsWith<WorkflowParseException> {
            parser.parse(
                """{"schemaVersion":1,"id":"flow","steps":[{"id":"one","type":"x","config":{"count":2}}]}"""
            )
        }
        assertTrue(nonStringConfig.message.orEmpty().contains("$.steps[0].config.count"))

        val duplicateSteps = parser.parse(
            """{"schemaVersion":1,"id":"flow","steps":[{"id":"same","type":"x"},{"id":"same","type":"x"}]}"""
        )
        assertFailsWith<WorkflowValidationException> {
            WorkflowValidator().validate(duplicateSteps)
        }

        val unknownHandler = parser.parse(
            """{"schemaVersion":1,"id":"flow","steps":[{"id":"one","type":"unknown"}]}"""
        )
        assertFailsWith<WorkflowStepHandlerNotFoundException> {
            WorkflowCompiler(WorkflowStepHandlerRegistry()).compile(unknownHandler)
        }
    }
}
