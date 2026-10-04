package me.moiclathy.edb.core.task.workflow.template

import me.moiclathy.edb.core.task.workflow.ir.WorkflowIr

data class WorkflowTemplate(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val version: String,
    val title: String,
    val description: String? = null,
    val category: String = "general",
    val tags: List<String> = emptyList(),
    val workflow: WorkflowIr
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported workflow template schema version: $schemaVersion." }
        require(id.isNotBlank() && version.isNotBlank() && title.isNotBlank()) { "Workflow template id, version and title must not be blank." }
        require(tags.none(String::isBlank)) { "Workflow template tags must not be blank." }
    }

    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}
