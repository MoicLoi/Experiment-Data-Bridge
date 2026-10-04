package me.moiclathy.edb.core.config.merge.model

/**
 * Configuration Merge 请求。
 *
 * 当前阶段只描述 Merge 所需要的输入，
 * 不负责执行 Merge。
 */
data class ConfigurationMergeRequest(

    /**
     * 三方 Merge 的共同基础版本。
     *
     * THREE_WAY 策略下必须提供。
     */
    val base: ConfigurationMergeSource?,

    /**
     * Source 版本。
     */
    val source: ConfigurationMergeSource,

    /**
     * Target 版本。
     */
    val target: ConfigurationMergeSource,

    /**
     * Merge 策略。
     */
    val strategy: ConfigurationMergeStrategy =
        ConfigurationMergeStrategy.THREE_WAY
)