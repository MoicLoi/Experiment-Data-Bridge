package me.moiclathy.edb.core.config.runtime

class ConfigRefreshService(

    private val configurationCenter: ConfigurationCenter

) {

    fun refresh(
        projectId: Int
    ) {

        configurationCenter.reload(projectId)

    }

    fun remove(
        projectId: Int
    ) {

        configurationCenter.invalidate(projectId)

    }

}