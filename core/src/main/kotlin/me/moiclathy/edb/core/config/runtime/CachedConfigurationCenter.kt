package me.moiclathy.edb.core.config.runtime

import me.moiclathy.edb.core.config.ProjectConfig
import me.moiclathy.edb.core.config.runtime.ProjectConfigLoader
import java.util.concurrent.ConcurrentHashMap

class CachedConfigurationCenter(

    private val loader: ProjectConfigLoader

) : ConfigurationCenter {

    private val cache =
        ConcurrentHashMap<Int, ProjectConfig>()

    override fun get(
        projectId: Int
    ): ProjectConfig {

        return cache.computeIfAbsent(projectId) {

            loader.load(projectId)

        }

    }

    override fun reload(
        projectId: Int
    ) {

        cache[projectId] =
            loader.load(projectId)

    }

    override fun invalidate(
        projectId: Int
    ) {

        cache.remove(projectId)

    }

}