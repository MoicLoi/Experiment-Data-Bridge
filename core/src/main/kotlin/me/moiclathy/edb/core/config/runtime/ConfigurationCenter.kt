package me.moiclathy.edb.core.config.runtime

import me.moiclathy.edb.core.config.ProjectConfig

interface ConfigurationCenter {

    fun get(
        projectId: Int
    ): ProjectConfig

    fun reload(
        projectId: Int
    )

    fun invalidate(
        projectId: Int
    )

}