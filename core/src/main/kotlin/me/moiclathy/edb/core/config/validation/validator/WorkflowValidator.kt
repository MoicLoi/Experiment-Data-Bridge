package me.moiclathy.edb.core.config.validation.validator

import me.moiclathy.edb.core.config.ProjectConfig

class WorkflowValidator {

    fun validate(
        config: ProjectConfig
    ): List<String> {

        val errors = mutableListOf<String>()

        if (config.workflow.steps.isEmpty()) {
            errors += "workflow.steps is empty"
        }

        return errors
    }

}