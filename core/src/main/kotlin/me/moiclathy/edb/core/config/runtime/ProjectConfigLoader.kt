package me.moiclathy.edb.core.config.runtime

import me.moiclathy.edb.core.config.ProjectConfig

interface ProjectConfigLoader {

    fun load(
        projectId: Int
    ): ProjectConfig

}