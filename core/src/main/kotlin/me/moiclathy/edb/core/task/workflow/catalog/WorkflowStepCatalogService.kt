package me.moiclathy.edb.core.task.workflow.catalog

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerSource
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepSchemaRegistry
import me.moiclathy.edb.core.datafile.analysis.AnalysisCapabilityMetadata
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandlerRegistry
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhrasePattern
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhrasePatternRegistry

data class WorkflowStepCatalogEntry(
    val type: String,
    val version: String,
    val source: WorkflowStepHandlerSource,
    val title: String,
    val description: String?,
    val parameterSchema: JsonObject?,
    val capabilities: List<WorkflowStepCapabilityEntry> = emptyList(),
    val phrasePatterns: List<WorkflowPhrasePattern> = emptyList()
) {
    val schemaAvailable: Boolean get() = parameterSchema != null
}

data class WorkflowStepCapabilityEntry(
    val id: String,
    val titleKey: String,
    val descriptionKey: String?,
    val cliPath: List<String>,
    val sourceType: String,
    val sourceId: String,
    val sourceVersion: String,
    val phrasePatterns: List<WorkflowPhrasePattern> = emptyList()
)

class WorkflowStepCatalogEntryNotFoundException(type: String, version: String) : EDBException(
    "WORKFLOW_STEP_NOT_FOUND",
    "Workflow step is not available: $type@$version"
)

class WorkflowStepCatalogSchemaNotFoundException(type: String, version: String) : EDBException(
    "WORKFLOW_STEP_SCHEMA_NOT_FOUND",
    "Workflow step has no parameter schema: $type@$version"
)

/** Read-only capability view shared by HTTP, CLI, agents, and future editors. */
class WorkflowStepCatalogService(
    private val handlers: WorkflowStepHandlerRegistry,
    private val schemas: WorkflowStepSchemaRegistry,
    private val analysisHandlers: AnalysisHandlerRegistry? = null,
    private val phrasePatterns: WorkflowPhrasePatternRegistry = WorkflowPhrasePatternRegistry.builtIns()
) {
    fun list(): List<WorkflowStepCatalogEntry> = handlers.registeredHandlers()
        .flatMap { handler ->
            handler.supportedVersions.map { version -> entry(handler.type, version, handler.source) }
        }
        .sortedWith(compareBy<WorkflowStepCatalogEntry> { it.type }.thenBy { it.version })

    fun find(type: String, version: String = DEFAULT_VERSION): WorkflowStepCatalogEntry? {
        val handler = handlers.find(type) ?: return null
        if (version !in handler.supportedVersions) return null
        return entry(type, version, handler.source)
    }

    fun require(type: String, version: String = DEFAULT_VERSION): WorkflowStepCatalogEntry =
        find(type, version) ?: throw WorkflowStepCatalogEntryNotFoundException(type, version)

    fun requireSchema(type: String, version: String = DEFAULT_VERSION): JsonObject =
        require(type, version).parameterSchema ?: throw WorkflowStepCatalogSchemaNotFoundException(type, version)

    private fun entry(type: String, version: String, source: WorkflowStepHandlerSource): WorkflowStepCatalogEntry {
        val schema = schemas.find(type, version)?.schema
        return WorkflowStepCatalogEntry(
            type = type,
            version = version,
            source = source,
            title = schema.stringAnnotation("title") ?: type,
            description = schema.stringAnnotation("description"),
            parameterSchema = schema,
            capabilities = if (type == "datafile.analysis.run") analysisCapabilities() else emptyList(),
            phrasePatterns = phrasePatterns.find(type, version)
        )
    }

    private fun analysisCapabilities(): List<WorkflowStepCapabilityEntry> = analysisHandlers?.handlers().orEmpty()
        .mapNotNull { handler -> handler.capabilityMetadata?.toCatalogEntry(handler.id) }
        .sortedBy { it.id }

    companion object {
        const val DEFAULT_VERSION = "1"
    }
}

private fun AnalysisCapabilityMetadata.toCatalogEntry(id: String) = WorkflowStepCapabilityEntry(
    id, titleKey, descriptionKey, cliPath, sourceType, sourceId, sourceVersion, phrasePatterns
)

private fun JsonObject?.stringAnnotation(name: String): String? =
    (this?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content
