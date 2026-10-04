package me.moiclathy.edb.core.config.merge.model

/**
 * Merge 冲突。
 *
 * 表示同一个配置路径在多个来源中产生了无法自动解决的差异。
 */
data class ConfigurationMergeConflict(

    /**
     * YAML 配置路径。
     *
     * 例如：
     *
     * server.port
     * database.host
     * security.jwt.expiresIn
     */
    val path: String,

    /**
     * Base 中的值。
     */
    val baseValue: String?,

    /**
     * Source 中的值。
     */
    val sourceValue: String?,

    /**
     * Target 中的值。
     */
    val targetValue: String?,

    /**
     * 冲突原因。
     */
    val reason: String
)