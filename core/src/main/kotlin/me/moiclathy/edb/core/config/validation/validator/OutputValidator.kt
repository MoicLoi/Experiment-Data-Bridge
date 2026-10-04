package me.moiclathy.edb.core.config.validation.validator

import me.moiclathy.edb.core.config.ProjectConfig

class OutputValidator {

    fun validate(
        config: ProjectConfig
    ): List<String> {

        val errors = mutableListOf<String>()

        if (config.output.path.isBlank()) {
            errors += "output.path is empty"
        }

        if (config.output.format.isBlank()) {
            errors += "output.format is empty"
        }

        return errors
    }

}