package me.moiclathy.edb.core.config.merge.model


import me.moiclathy.edb.core.config.snapshot.model.ConfigurationSnapshot

/**
 * 带有元数据上下文的合并结果。
 */
data class ConfigurationMergeResult(
    val targetSnapshot: ConfigurationSnapshot,
    val sourceSnapshot: ConfigurationSnapshot,
    val mergedYaml: String?,
    val conflicts: List<ConfigurationMergeConflict>
) {

    val isSuccess: Boolean
        get() = conflicts.isEmpty()

    companion object {
        fun success(
            target: ConfigurationSnapshot,
            source: ConfigurationSnapshot,
            yaml: String
        ) = ConfigurationMergeResult(target, source, yaml, emptyList())

        fun conflict(
            target: ConfigurationSnapshot,
            source: ConfigurationSnapshot,
            conflicts: List<ConfigurationMergeConflict>
        ) = ConfigurationMergeResult(target, source, null, conflicts)
    }
}