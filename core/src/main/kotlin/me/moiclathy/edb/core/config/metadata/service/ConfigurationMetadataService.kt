package me.moiclathy.edb.core.config.metadata.service

import me.moiclathy.edb.core.config.metadata.model.ConfigurationMetadata
import me.moiclathy.edb.core.config.metadata.repository.ConfigurationMetadataRepository
import java.time.Instant

/**
 * Configuration Metadata Service
 *
 * 负责：
 * - 创建 / 保存配置元数据
 * - 查询项目元数据
 * - 更新配置元数据
 * - 删除配置元数据
 * - 管理 Metadata Tags
 *
 * Metadata 与 Project 一一对应。
 */
class ConfigurationMetadataService(
    private val repository: ConfigurationMetadataRepository
) {

    /**
     * 获取指定项目的 Configuration Metadata。
     */
    fun get(
        projectId: Int
    ): ConfigurationMetadata? {

        require(projectId > 0) {
            "Project ID must be greater than zero."
        }

        return repository.findByProjectId(projectId)
    }

    /**
     * 创建新的 Configuration Metadata。
     *
     * 一个 project 只能拥有一份 metadata。
     */
    fun create(
        projectId: Int,
        description: String?,
        category: String?,
        tags: List<String>,
        dataSource: String?
    ): ConfigurationMetadata {

        require(projectId > 0) {
            "Project ID must be greater than zero."
        }

        require(!repository.exists(projectId)) {
            "Configuration metadata already exists for project: $projectId"
        }

        val now = Instant.now()

        val metadata =
            ConfigurationMetadata(
                projectId = projectId,
                description = description,
                category = category,
                tags = normalizeTags(tags),
                updatedAt = now,
                dataSource = dataSource
            )

        return repository.save(metadata)
    }

    /**
     * 保存或覆盖指定项目的 Metadata。
     */
    fun save(
        metadata: ConfigurationMetadata
    ): ConfigurationMetadata {

        validateProjectId(metadata.projectId)

        val normalized =
            metadata.copy(
                tags = normalizeTags(metadata.tags),
                updatedAt = Instant.now()
            )

        return repository.save(normalized)
    }

    /**
     * Creates or replaces metadata restored from a package while preserving
     * the timestamp recorded by the source package.
     */
    fun importMetadata(
        metadata: ConfigurationMetadata
    ): ConfigurationMetadata {
        validateProjectId(metadata.projectId)

        val normalized = metadata.copy(
            tags = normalizeTags(metadata.tags)
        )

        return if (repository.exists(metadata.projectId)) {
            repository.update(normalized)
        } else {
            repository.save(normalized)
        }
    }

    /**
     * 更新指定项目的 Metadata。
     */
    fun update(
        projectId: Int,
        description: String?,
        category: String?,
        tags: List<String>,
        dataSource: String?
    ): ConfigurationMetadata {

        validateProjectId(projectId)

        require(repository.exists(projectId)) {
            "Configuration metadata not found for project: $projectId"
        }

        val metadata =
            ConfigurationMetadata(
                projectId = projectId,
                description = description,
                category = category,
                tags = normalizeTags(tags),
                updatedAt = Instant.now(),
                dataSource = dataSource
            )

        return repository.update(metadata)
    }

    /**
     * 删除指定项目的 Metadata。
     */
    fun delete(
        projectId: Int
    ) {

        validateProjectId(projectId)

        repository.delete(projectId)
    }

    /**
     * 判断指定项目是否存在 Metadata。
     */
    fun exists(
        projectId: Int
    ): Boolean {

        validateProjectId(projectId)

        return repository.exists(projectId)
    }

    // =========================================================
    // Tag API
    // =========================================================

    /**
     * 获取指定项目的全部 Tags。
     */
    fun listTags(
        projectId: Int
    ): List<String> {

        validateProjectId(projectId)

        val metadata =
            repository.findByProjectId(projectId)
                ?: throw IllegalArgumentException(
                    "Configuration metadata not found for project: $projectId"
                )

        return metadata.tags
    }

    /**
     * 向指定项目添加一个 Tag。
     *
     * Tag 规则：
     * - 不能为空
     * - 自动 trim
     * - 大小写保持原样
     * - 已存在时不重复添加
     */
    fun addTag(
        projectId: Int,
        tag: String
    ): ConfigurationMetadata {

        validateProjectId(projectId)

        val normalizedTag =
            normalizeTag(tag)

        val metadata =
            repository.findByProjectId(projectId)
                ?: throw IllegalArgumentException(
                    "Configuration metadata not found for project: $projectId"
                )

        if (metadata.tags.contains(normalizedTag)) {
            return metadata
        }

        val updated =
            metadata.copy(
                tags = normalizeTags(
                    metadata.tags + normalizedTag
                ),
                updatedAt = Instant.now()
            )

        return repository.update(updated)
    }

    /**
     * 删除指定项目中的 Tag。
     *
     * 如果 Tag 不存在，则保持当前 Metadata 不变。
     */
    fun removeTag(
        projectId: Int,
        tag: String
    ): ConfigurationMetadata {

        validateProjectId(projectId)

        val normalizedTag =
            normalizeTag(tag)

        val metadata =
            repository.findByProjectId(projectId)
                ?: throw IllegalArgumentException(
                    "Configuration metadata not found for project: $projectId"
                )

        if (!metadata.tags.contains(normalizedTag)) {
            return metadata
        }

        val updated =
            metadata.copy(
                tags = metadata.tags.filter {
                    it != normalizedTag
                },
                updatedAt = Instant.now()
            )

        return repository.update(updated)
    }

    /**
     * 对单个 Tag 进行规范化。
     */
    private fun normalizeTag(
        tag: String
    ): String {

        val normalized =
            tag.trim()

        require(normalized.isNotBlank()) {
            "Tag must not be blank."
        }

        return normalized
    }

    /**
     * 对整个 Tag 列表进行统一规范化：
     *
     * - 去除首尾空格
     * - 删除空 Tag
     * - 去重
     */
    private fun normalizeTags(
        tags: List<String>
    ): List<String> {

        return tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun validateProjectId(
        projectId: Int
    ) {

        require(projectId > 0) {
            "Project ID must be greater than zero."
        }
    }
}
