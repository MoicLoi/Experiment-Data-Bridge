package me.moiclathy.edb.core.config.validation

import me.moiclathy.edb.core.config.ProjectConfig

interface ConfigValidator {

    fun validate(
        config: ProjectConfig
    ): ValidationResult

}