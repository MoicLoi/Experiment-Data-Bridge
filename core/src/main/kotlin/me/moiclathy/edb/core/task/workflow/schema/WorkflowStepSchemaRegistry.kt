package me.moiclathy.edb.core.task.workflow.schema

import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

data class WorkflowStepSchemaKey(
    val type: String,
    val version: String
) {
    init {
        require(type.isNotBlank() && type == type.trim()) { "Workflow step schema type is invalid." }
        require(version.isNotBlank() && version == version.trim()) { "Workflow step schema version is invalid." }
    }
}

data class WorkflowStepParameterSchema(
    val type: String,
    val version: String,
    val schema: JsonObject
) {
    val key: WorkflowStepSchemaKey = WorkflowStepSchemaKey(type, version)
}

class WorkflowStepSchemaNotFoundException(type: String, version: String) :
    NoSuchElementException("Workflow step parameter schema is not registered: $type@$version")

class WorkflowStepSchemaRegistrationException(message: String) : IllegalArgumentException(message)

/** Thread-safe registry keyed by the exact step type and version used by Workflow IR. */
class WorkflowStepSchemaRegistry(
    schemas: Iterable<WorkflowStepParameterSchema> = emptyList(),
    private val schemaValidator: WorkflowJsonSchemaValidator = WorkflowJsonSchemaValidator()
) {
    private val schemasByKey = ConcurrentHashMap<WorkflowStepSchemaKey, WorkflowStepParameterSchema>()

    init {
        schemas.forEach(::register)
    }

    fun register(schema: WorkflowStepParameterSchema) {
        schemaValidator.validateSchemaDefinition(schema.schema)
        val previous = schemasByKey.putIfAbsent(schema.key, schema)
        if (previous != null) throw WorkflowStepSchemaRegistrationException(
            "Workflow step parameter schema is already registered: ${schema.type}@${schema.version}"
        )
    }

    fun find(type: String, version: String): WorkflowStepParameterSchema? =
        schemasByKey[WorkflowStepSchemaKey(type, version)]

    fun require(type: String, version: String): WorkflowStepParameterSchema =
        find(type, version) ?: throw WorkflowStepSchemaNotFoundException(type, version)

    fun registeredSchemas(): List<WorkflowStepParameterSchema> =
        schemasByKey.values.sortedWith(compareBy<WorkflowStepParameterSchema> { it.type }.thenBy { it.version })

    fun registeredKeys(): Set<WorkflowStepSchemaKey> = schemasByKey.keys.toSet()
}
