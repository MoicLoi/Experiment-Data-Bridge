package me.moiclathy.edb.cli.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkflowCliServiceTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `validates and lists workflow files using production handlers`() {
        val plugins = Files.createDirectory(directory.resolve("plugins"))
        val workflows = Files.createDirectory(directory.resolve("workflows"))
        val workflow = workflows.resolve("sample.workflow.json")
        Files.writeString(workflow, validWorkflow("sample"))
        val service = WorkflowCliService()

        assertEquals("sample", service.validate(workflow, plugins).id)
        assertEquals(listOf("sample"), service.list(workflows, plugins).map { it.id })
    }

    @Test
    fun `rejects an unknown workflow step handler`() {
        val plugins = Files.createDirectory(directory.resolve("plugins"))
        val workflow = directory.resolve("invalid.workflow.json")
        Files.writeString(workflow, validWorkflow("invalid", "missing.handler"))

        assertFailsWith<IllegalArgumentException> {
            WorkflowCliService().validate(workflow, plugins)
        }
    }

    private fun validWorkflow(id: String, type: String = "context.set"): String =
        """
        {
          "schemaVersion": 1,
          "id": "$id",
          "steps": [
            {
              "id": "initialize",
              "type": "$type",
              "config": { "key": "source", "value": "raw.csv" }
            }
          ]
        }
        """.trimIndent()
}
