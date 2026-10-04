package me.moiclathy.edb.core.config.packages

import java.time.Instant

data class EdbManifest(
    val packageVersion: Int,
    val edbVersion: String,
    val projectName: String,
    val projectId: Int?,
    val exportedAt: Instant,
    val author: String?,
    val metadata: EdbManifestMetadata? = null
)
