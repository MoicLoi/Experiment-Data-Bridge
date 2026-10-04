package me.moiclathy.edb.core.task.workflow

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Serializable workflow description. It contains configuration, never executable code. */
data class WorkflowDefinition(
    val schemaVersion: Int,
    val id: String,
    val name: String? = null,
    val steps: List<WorkflowStepDefinition>
)

data class WorkflowStepDefinition(
    val id: String,
    val type: String,
    val config: Map<String, String> = emptyMap(),
    val version: String = "1",
    val parameters: JsonObject = JsonObject(config.mapValues { JsonPrimitive(it.value) })
)
