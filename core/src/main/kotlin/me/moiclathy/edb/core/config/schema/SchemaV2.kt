package me.moiclathy.edb.core.config.schema

import me.moiclathy.edb.core.config.ProjectConfig

class SchemaV2 : SchemaV1() {

    override val version = 2

    override fun validate(
        config: ProjectConfig
    ): List<ConfigValidationError> {

        val errors = super.validate(config).toMutableList()

        // V2 新增规则

        return errors
    }
}