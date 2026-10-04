package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.config.metadata.model.ConfigurationMetadata
import java.time.Instant

/**
 * Configuration Metadata HTTP Response。
 */
@Serializable
data class ConfigurationMetadataResponse(

    val projectId: Int,

    val description: String?,

    val category: String?,

    val tags: List<String>,

    val updatedAt: String,

    val dataSource: String?
) {

    companion object {

        fun from(
            metadata: ConfigurationMetadata
        ): ConfigurationMetadataResponse {

            return ConfigurationMetadataResponse(
                projectId = metadata.projectId,
                description = metadata.description,
                category = metadata.category,
                tags = metadata.tags,
                updatedAt = metadata.updatedAt.toString(),
                dataSource = metadata.dataSource
            )
        }
    }
}


/**
 * 创建 Metadata 请求。
 */
@Serializable
data class CreateConfigurationMetadataRequest(

    val description: String? = null,

    val category: String? = null,

    val tags: List<String> = emptyList(),

    val dataSource: String? = null
)


/**
 * 更新 Metadata 请求。
 */
@Serializable
data class UpdateConfigurationMetadataRequest(

    val description: String? = null,

    val category: String? = null,

    val tags: List<String> = emptyList(),

    val dataSource: String? = null
)

@Serializable
data class ConfigurationMetadataTagRequest(

    val tag: String
)


@Serializable
data class ConfigurationMetadataTagsResponse(

    val projectId: Int,

    val tags: List<String>
)