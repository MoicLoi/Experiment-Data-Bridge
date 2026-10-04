package me.moiclathy.edb.core.config.metadata.repository

import me.moiclathy.edb.core.config.metadata.model.ConfigurationMetadata

/**
 * Configuration Metadata Repository。
 *
 * 一个 projectId 对应一份 ConfigurationMetadata。
 */
interface ConfigurationMetadataRepository {

    /**
     * 保存配置元数据。
     *
     * 如果 projectId 已存在，由实现层决定是否覆盖。
     */
    fun save(
        metadata: ConfigurationMetadata
    ): ConfigurationMetadata

    /**
     * 根据 projectId 查询配置元数据。
     */
    fun findByProjectId(
        projectId: Int
    ): ConfigurationMetadata?

    /**
     * 更新配置元数据。
     */
    fun update(
        metadata: ConfigurationMetadata
    ): ConfigurationMetadata

    /**
     * 删除指定项目的配置元数据。
     */
    fun delete(
        projectId: Int
    )

    /**
     * 判断指定项目是否存在配置元数据。
     */
    fun exists(
        projectId: Int
    ): Boolean

    /**
     * 查询全部配置元数据。
     *
     * 后续 Configuration Metadata API 可以直接复用。
     */
    fun findAll(): List<ConfigurationMetadata>
}