package me.moiclathy.edb.web.dto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.template.WorkflowTemplate

data class WorkflowTemplateCatalogResponse(val items: List<WorkflowTemplateResponse>)

data class WorkflowTemplateResponse(
    val schemaVersion: Int,
    val id: String,
    val version: String,
    val title: String,
    val description: String?,
    val category: String,
    val tags: List<String>,
    val workflow: JsonElement
) {
    companion object {
        fun from(template: WorkflowTemplate, codec: WorkflowIrCodec) = WorkflowTemplateResponse(
            template.schemaVersion, template.id, template.version, template.title,
            template.description, template.category, template.tags,
            Json.parseToJsonElement(codec.serialize(template.workflow))
        )
    }
}
