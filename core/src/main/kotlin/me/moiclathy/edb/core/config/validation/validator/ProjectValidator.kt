package me.moiclathy.edb.core.config.validation.validator

import me.moiclathy.edb.core.config.ProjectConfig

class ProjectValidator {

    fun validate(
        config: ProjectConfig
    ): List<String> {

        val errors = mutableListOf<String>()

        if (config.project.name.isBlank()) {
            errors += "project.name is empty"
        }

        if (config.project.species.isBlank()) {
            errors += "project.species is empty"
        }

        return errors
    }

}