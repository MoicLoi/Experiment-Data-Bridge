package me.moiclathy.edb.core.config.schema

import me.moiclathy.edb.core.config.ProjectConfig

interface ConfigSchemaDefinition {

    val version: Int

    fun validate(

        config: ProjectConfig

    ): List<ConfigValidationError>

}