package me.moiclathy.edb.core.config.schema

import me.moiclathy.edb.core.config.ProjectConfig

open class SchemaV1 : ConfigSchemaDefinition {

    override val version = 1

    override fun validate(

        config: ProjectConfig

    ): List<ConfigValidationError> {

        val errors = mutableListOf<ConfigValidationError>()

        if (config.project.name.isBlank()) {
            errors += ConfigValidationError(
                field = "project.name",
                message = "Project name cannot be blank"
            )
        }

        if (config.workflow.steps.isEmpty()) {
            errors += ConfigValidationError(
                field = "workflow.steps",
                message = "Workflow must contain at least one step"
            )
        }

        if (config.output.format.isBlank()) {
            errors += ConfigValidationError(
                field = "output.format",
                message = "Output format cannot be blank"
            )
        }

        if (config.output.path.isBlank()) {
            errors += ConfigValidationError(
                field = "output.path",
                message = "Output path cannot be blank"
            )
        }

        return errors

    }

}