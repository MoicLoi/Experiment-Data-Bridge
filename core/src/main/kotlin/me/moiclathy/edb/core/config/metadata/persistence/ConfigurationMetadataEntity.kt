package me.moiclathy.edb.core.config.metadata.persistence

import java.time.Instant

/**
 * Configuration Metadata 的数据库实体。
 *
 * tags 在数据库中暂时以 JSON 字符串保存。
 */
data class ConfigurationMetadataEntity(

    val projectId: Int,

    val description: String?,

    val category: String?,

    val tags: String,

    val updatedAt: Instant,

    val dataSource: String?
)