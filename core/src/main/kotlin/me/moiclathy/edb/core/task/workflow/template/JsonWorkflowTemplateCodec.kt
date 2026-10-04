package me.moiclathy.edb.core.task.workflow.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCodec

class JsonWorkflowTemplateCodec(private val workflowCodec: WorkflowIrCodec, private val json: Json = Json) {
    fun deserialize(document: String): WorkflowTemplate {
        val root = json.parseToJsonElement(document) as? JsonObject ?: error("Workflow template must be a JSON object.")
        val allowed = setOf("schemaVersion", "id", "version", "title", "description", "category", "tags", "workflow")
        require((root.keys - allowed).isEmpty()) { "Unknown workflow template property: ${(root.keys - allowed).first()}." }
        fun string(name: String, required: Boolean = true): String? {
            val value = root[name] ?: return if (required) error("Workflow template property '$name' is required.") else null
            return (value as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
                ?: error("Workflow template property '$name' must be a string.")
        }
        val schemaVersion = (root["schemaVersion"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: error("Workflow template schemaVersion must be an integer.")
        val tags = (root["tags"] ?: JsonArray(emptyList())).let { value ->
            (value as? JsonArray)?.map { item ->
                (item as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content ?: error("Workflow template tags must contain strings.")
            } ?: error("Workflow template tags must be an array.")
        }
        val workflow = root["workflow"] as? JsonObject ?: error("Workflow template workflow must be an object.")
        return WorkflowTemplate(schemaVersion, string("id")!!, string("version")!!, string("title")!!,
            string("description", false), string("category", false) ?: "general", tags, workflowCodec.deserialize(workflow.toString()))
    }
}
