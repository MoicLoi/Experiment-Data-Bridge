package me.moiclathy.edb.core.config.importer

import me.moiclathy.edb.core.git.GitCommitInfo

data class ImportReport(

    val originalVersion: Int,

    val migratedVersion: Int,

    val migrationApplied: Boolean,

    val commit: GitCommitInfo

)