package me.moiclathy.edb.core.config.metadata.persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.config.metadata.model.ConfigurationMetadata

/**
 * ConfigurationMetadata
 * <-> ConfigurationMetadataEntity
 */
object ConfigurationMetadataMapper {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun toEntity(
        metadata: ConfigurationMetadata
    ): ConfigurationMetadataEntity {

        return ConfigurationMetadataEntity(
            projectId = metadata.projectId,
            description = metadata.description,
            category = metadata.category,
            tags = json.encodeToString(metadata.tags),
            updatedAt = metadata.updatedAt,
            dataSource = metadata.dataSource
        )
    }

    fun toModel(
        entity: ConfigurationMetadataEntity
    ): ConfigurationMetadata {

        return ConfigurationMetadata(
            projectId = entity.projectId,
            description = entity.description,
            category = entity.category,
            tags = decodeTags(entity.tags),
            updatedAt = entity.updatedAt,
            dataSource = entity.dataSource
        )
    }

    private fun decodeTags(
        value: String
    ): List<String> {

        if (value.isBlank()) {
            return emptyList()
        }

        return json.decodeFromString<List<String>>(
            value
        )
    }
}