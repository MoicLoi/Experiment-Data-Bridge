package me.moiclathy.edb.core.git

import me.moiclathy.edb.core.config.preview.ConfigurationPreview

class GitPreviewService(

    private val loader: GitFileLoader

){
    fun preview(
        projectId: Int,
        commitId: String
    ): ConfigurationPreview {
        val yaml = loader.loadConfig(
            projectId,
            commitId
        )
        return ConfigurationPreview(
            yaml = yaml
        )
    }
}