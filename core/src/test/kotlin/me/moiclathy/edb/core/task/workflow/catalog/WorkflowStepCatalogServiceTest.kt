package me.moiclathy.edb.core.task.workflow.catalog

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.workflow.SetContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerSource
import me.moiclathy.edb.core.task.workflow.schema.BuiltInWorkflowStepSchemas
import me.moiclathy.edb.core.task.workflow.schema.WorkflowJsonSchemaValidator
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepParameterSchema
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepSchemaRegistry
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhrasePattern
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhrasePatternRegistry
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhraseToken
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhraseTokenType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowStepCatalogServiceTest {
    @Test
    fun `merges executable handler versions with available schemas`() {
        val plugin = object : WorkflowStepHandler {
            override val type = "plugin.echo"
            override val supportedVersions = setOf("1", "2")
            override val source = WorkflowStepHandlerSource.PLUGIN
            override suspend fun execute(step: WorkflowStepDefinition, context: PipelineContext) =
                PipelineStepResult.Success(context)
        }
        val pluginV2 = WorkflowStepParameterSchema(plugin.type, "2", buildJsonObject {
            put("\$schema", WorkflowJsonSchemaValidator.DIALECT)
            put("title", "Plugin Echo")
            put("description", "Echo plugin parameters")
            put("type", "object")
        })
        val service = WorkflowStepCatalogService(
            WorkflowStepHandlerRegistry(listOf(SetContextWorkflowStepHandler(), plugin)),
            WorkflowStepSchemaRegistry(BuiltInWorkflowStepSchemas.all() + pluginV2)
        )

        val entries = service.list()

        assertEquals(listOf("context.set@1", "plugin.echo@1", "plugin.echo@2"), entries.map { "${it.type}@${it.version}" })
        assertEquals(WorkflowStepHandlerSource.PLUGIN, service.require("plugin.echo", "2").source)
        assertEquals("Plugin Echo", service.require("plugin.echo", "2").title)
        assertTrue(service.require("plugin.echo", "2").schemaAvailable)
        assertEquals(listOf("step-short"), service.require("context.set").phrasePatterns.map { it.id })
        assertFalse(service.require("plugin.echo", "1").schemaAvailable)
        assertFailsWith<WorkflowStepCatalogSchemaNotFoundException> { service.requireSchema("plugin.echo", "1") }
        assertFailsWith<WorkflowStepCatalogEntryNotFoundException> { service.require("plugin.echo", "3") }
    }

    @Test
    fun `phrase registry supports multiple templates and rejects duplicates`() {
        val first = WorkflowPhrasePattern("convert-short", "datafile.convert", tokens = listOf(
            WorkflowPhraseToken(WorkflowPhraseTokenType.ENUM, name = "targetFormat", source = "conversion.formats")
        ))
        val registry = WorkflowPhrasePatternRegistry(listOf(first.copy(id = "convert-natural"), first))
        assertEquals(listOf("convert-natural", "convert-short"), registry.find("datafile.convert").map { it.id })
        assertFailsWith<IllegalArgumentException> { registry.register(first) }
    }
}
