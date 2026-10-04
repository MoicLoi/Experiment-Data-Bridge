package me.moiclathy.edb.core.git

object GitCommitMessages {

    fun configurationUpdated(
        projectName: String
    ): String =
        "Update configuration of $projectName"


    fun configurationImported(
        projectName: String
    ): String =
        "Import configuration into $projectName"
}