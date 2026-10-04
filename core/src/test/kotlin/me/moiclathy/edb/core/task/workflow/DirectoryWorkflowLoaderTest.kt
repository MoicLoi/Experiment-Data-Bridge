package me.moiclathy.edb.core.task.workflow

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.SequentialPipelineExecutor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DirectoryWorkflowLoaderTest {
    @Test
    fun `loads matching top-level files in stable order and registers pipelines`(
        @TempDir directory: Path
    ) = runBlocking {
        Files.writeString(directory.resolve("b.workflow.json"), workflowJson("second", "b"))
        Files.writeString(directory.resolve("a.workflow.json"), workflowJson("first", "a"))
        Files.writeString(directory.resolve("notes.json"), workflowJson("ignored", "ignored"))
        val nested = Files.createDirectory(directory.resolve("nested"))
        Files.writeString(nested.resolve("nested.workflow.json"), workflowJson("nested", "nested"))
        val registry = PipelineRegistry()

        val result = loader(registry).load(directory)

        assertEquals(listOf("a.workflow.json", "b.workflow.json"), result.files.map { it.fileName.toString() })
        assertEquals(listOf("first", "second"), result.pipelineIds)
        assertEquals(setOf("first", "second"), registry.registeredIds())
        val execution = SequentialPipelineExecutor().execute(assertNotNull(registry.find("first")))
        assertEquals("a", execution.context.getAs<String>("loaded"))
    }

    @Test
    fun `does not register any pipeline when one workflow file is invalid`(
        @TempDir directory: Path
    ) {
        Files.writeString(directory.resolve("a.workflow.json"), workflowJson("valid", "a"))
        Files.writeString(
            directory.resolve("b.workflow.json"),
            """{"schemaVersion":1,"id":"invalid","steps":[{"id":"run","type":"missing"}]}"""
        )
        val registry = PipelineRegistry()

        val failure = assertFailsWith<WorkflowFileLoadException> {
            loader(registry).load(directory)
        }

        assertEquals("b.workflow.json", failure.file.fileName.toString())
        assertEquals(emptySet(), registry.registeredIds())
    }

    private fun loader(registry: PipelineRegistry): DirectoryWorkflowLoader {
        val handlerRegistry = WorkflowStepHandlerRegistry(
            listOf(
                object : WorkflowStepHandler {
                    override val type = "test.put"

                    override suspend fun execute(
                        step: WorkflowStepDefinition,
                        context: PipelineContext
                    ): PipelineStepResult = PipelineStepResult.Success(
                        context.withValue("loaded", step.config["value"])
                    )
                }
            )
        )
        return DirectoryWorkflowLoader(
            driver = JsonWorkflowDriver(
                parser = KotlinxJsonWorkflowParser(),
                compiler = WorkflowCompiler(handlerRegistry)
            ),
            pipelineRegistry = registry
        )
    }

    private fun workflowJson(id: String, value: String): String =
        """
        {
          "schemaVersion": 1,
          "id": "$id",
          "steps": [
            { "id": "run", "type": "test.put", "config": { "value": "$value" } }
          ]
        }
        """.trimIndent()
}
