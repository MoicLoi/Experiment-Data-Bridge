package me.moiclathy.edb.web.dto

import me.moiclathy.edb.core.config.merge.MergeStrategy

data class ConfigurationMergeRequest(
    val targetSnapshotId: Long,
    val sourceSnapshotId: Long,
    val strategy: MergeStrategy = MergeStrategy.FAIL_ON_CONFLICT
)

data class ConfigurationMergeResponse(
    val targetSnapshotId: Long,
    val sourceSnapshotId: Long,
    val mergedYaml: String
)

data class ConfigurationMergeConflictResponse(
    val path: String,
    val baseValue: String?,
    val sourceValue: String?,
    val targetValue: String?,
    val reason: String
)

data class ConfigurationMergeConflictResponseBody(
    val targetSnapshotId: Long,
    val sourceSnapshotId: Long,
    val conflicts: List<ConfigurationMergeConflictResponse>
)