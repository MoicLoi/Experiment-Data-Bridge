package me.moiclathy.edb.core.config.merge.model

/**
 * Merge 的一个配置来源。
 *
 * source 可以来自：
 *
 * - 当前 Project Configuration
 * - Configuration Snapshot
 * - Git Commit
 * - Template
 * - 未来其他配置来源
 */
data class ConfigurationMergeSource(

    /**
     * 来源标识。
     *
     * 可以是：
     * snapshot id
     * git commit id
     * project configuration id
     * template id
     */
    val id: String,

    /**
     * 来源类型。
     */
    val type: Type,

    /**
     * YAML 配置正文。
     */
    val yaml: String
) {

    enum class Type {

        PROJECT_CONFIGURATION,

        SNAPSHOT,

        GIT_COMMIT,

        TEMPLATE
    }
}