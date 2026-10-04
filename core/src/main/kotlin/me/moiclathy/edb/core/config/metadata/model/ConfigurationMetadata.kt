package me.moiclathy.edb.core.config.metadata.model

import java.time.Instant

data class ConfigurationMetadata(
    val projectId: Int,
    val description: String?,
    val category: String?,
    val tags: List<String>,
    val updatedAt: Instant,
    val dataSource: String?
)