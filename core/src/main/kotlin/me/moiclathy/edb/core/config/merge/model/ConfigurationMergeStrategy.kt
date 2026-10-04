package me.moiclathy.edb.core.config.merge.model

/**
 * Configuration Merge 的策略。
 *
 * THREE_WAY：
 * 使用 base / source / target 进行三方 Merge。
 *
 * SOURCE_WINS：
 * 冲突时优先使用 source。
 *
 * TARGET_WINS：
 * 冲突时优先使用 target。
 */
enum class ConfigurationMergeStrategy {

    THREE_WAY,

    SOURCE_WINS,

    TARGET_WINS
}