package me.moiclathy.edb.core.config.compare.model

data class ConfigurationCompareChange(

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

    val type: ConfigurationCompareChangeType,

    /**
     * Source Snapshot 中的值。
     */
    val sourceValue: Any?,

    /**
     * Target Snapshot 中的值。
     */
    val targetValue: Any?
)