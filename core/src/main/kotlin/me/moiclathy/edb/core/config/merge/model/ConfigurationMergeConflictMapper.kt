package me.moiclathy.edb.core.config.merge.model

import me.moiclathy.edb.core.config.merge.MergeConflict

/**
 * 将 MergeEngine 层的 MergeConflict
 * 转换为 Configuration Snapshot Merge 层的冲突模型。
 *
 * 当前 MergeEngine 使用 target + source 双方合并，
 * 因此当前不存在独立的 base snapshot。
 */
object ConfigurationMergeConflictMapper {

    fun toModel(
        conflict: MergeConflict
    ): ConfigurationMergeConflict {

        return ConfigurationMergeConflict(
            path = conflict.path,
            baseValue = null,
            sourceValue = conflict.sourceValue?.toString(),
            targetValue = conflict.targetValue?.toString(),
            reason = "${conflict.message} [${conflict.type}]"
        )
    }

    fun toModel(
        conflicts: List<MergeConflict>
    ): List<ConfigurationMergeConflict> {

        return conflicts.map(::toModel)
    }
}