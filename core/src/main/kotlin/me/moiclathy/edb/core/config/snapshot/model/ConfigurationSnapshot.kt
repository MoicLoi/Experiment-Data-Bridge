package me.moiclathy.edb.core.config.snapshot.model

data class ConfigurationSnapshot(

    val id: Long?,

    val projectId: Int,

    val version: Int,

    val metadata: SnapshotMetadata,

    /**
     * Snapshot 保存的是创建时配置的完整 YAML。
     *
     * Snapshot 本身是不可变配置状态。
     */
    val yaml: String
)