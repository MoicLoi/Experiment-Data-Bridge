package me.moiclathy.edb.core.config.mapper

import me.moiclathy.edb.core.config.ProjectConfig

interface ProjectConfigMapper {

    fun fromYaml(
        yaml: String
    ): ProjectConfig

    fun toYaml(
        config: ProjectConfig
    ): String

}