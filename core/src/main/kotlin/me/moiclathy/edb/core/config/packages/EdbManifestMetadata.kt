package me.moiclathy.edb.core.config.packages

import java.time.Instant

/**
 * Portable metadata stored in an .edb manifest.
 *
 * The project ID intentionally lives only on [EdbManifest]. When a package is
 * imported into another project, metadata is bound to the target project.
 */
data class EdbManifestMetadata(
    val description: String?,
    val category: String?,
    val tags: List<String>,
    val updatedAt: Instant,
    val dataSource: String?
)
