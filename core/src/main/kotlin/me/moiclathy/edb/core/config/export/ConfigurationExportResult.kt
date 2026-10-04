package me.moiclathy.edb.core.config.export

data class ConfigurationExportResult(

    val fileName: String,

    val yaml: String,

    val projectId: Int,

    val projectName: String,

    val author: String?

)