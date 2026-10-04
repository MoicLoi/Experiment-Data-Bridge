package me.moiclathy.edb.core.task.workflow

data class WorkflowValidationError(
    val path: String,
    val message: String
)

class WorkflowValidationException(
    val errors: List<WorkflowValidationError>
) : IllegalArgumentException(
    errors.joinToString(
        prefix = "Workflow definition is invalid: ",
        separator = "; "
    ) { "${it.path}: ${it.message}" }
)

class WorkflowValidator(
    private val supportedSchemaVersions: Set<Int> = setOf(CURRENT_SCHEMA_VERSION)
) {
    init {
        require(supportedSchemaVersions.isNotEmpty()) {
            "At least one workflow schema version must be supported."
        }
    }

    fun validate(definition: WorkflowDefinition): WorkflowDefinition {
        val errors = buildList {
            if (definition.schemaVersion !in supportedSchemaVersions) {
                add(
                    WorkflowValidationError(
                        "$.schemaVersion",
                        "Unsupported version ${definition.schemaVersion}; supported versions: " +
                            supportedSchemaVersions.sorted().joinToString()
                    )
                )
            }
            validateIdentifier(definition.id, "$.id", "Workflow id", this)
            if (definition.name != null && definition.name.isBlank()) {
                add(WorkflowValidationError("$.name", "Workflow name must not be blank."))
            }
            if (definition.steps.isEmpty()) {
                add(WorkflowValidationError("$.steps", "Workflow must contain at least one step."))
            }

            val duplicateIds = definition.steps.groupingBy { it.id }.eachCount()
                .filterValues { it > 1 }
                .keys
            definition.steps.forEachIndexed { index, step ->
                val path = "$.steps[$index]"
                validateIdentifier(step.id, "$path.id", "Step id", this)
                validateIdentifier(step.type, "$path.type", "Step type", this)
                step.config.keys.filter { it.isBlank() }.forEach {
                    add(WorkflowValidationError("$path.config", "Configuration keys must not be blank."))
                }
                if (step.id in duplicateIds) {
                    add(WorkflowValidationError("$path.id", "Duplicate step id: ${step.id}"))
                }
            }
        }

        if (errors.isNotEmpty()) throw WorkflowValidationException(errors)
        return definition
    }

    private fun validateIdentifier(
        value: String,
        path: String,
        label: String,
        errors: MutableList<WorkflowValidationError>
    ) {
        when {
            value.isBlank() -> errors += WorkflowValidationError(path, "$label must not be blank.")
            value != value.trim() -> errors += WorkflowValidationError(
                path,
                "$label must not contain leading or trailing whitespace."
            )
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

