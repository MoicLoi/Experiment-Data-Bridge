package me.moiclathy.edb.core.config.compare.model

enum class ConfigurationCompareChangeType {

    /**
     * source 与 target 完全一致。
     */
    UNCHANGED,

    /**
     * source 中不存在，target 中新增。
     */
    ADDED,

    /**
     * source 中存在，target 中已经删除。
     */
    REMOVED,

    /**
     * source 与 target 均存在，但值不同。
     */
    CHANGED
}