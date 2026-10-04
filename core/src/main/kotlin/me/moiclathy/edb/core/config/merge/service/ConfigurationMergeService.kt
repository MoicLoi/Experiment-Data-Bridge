package me.moiclathy.edb.core.config.merge.service

import me.moiclathy.edb.core.config.merge.MergeEngine
import me.moiclathy.edb.core.config.merge.MergeRequest
import me.moiclathy.edb.core.config.merge.MergeStrategy
import me.moiclathy.edb.core.config.merge.exception.SnapshotMergeException
import me.moiclathy.edb.core.config.merge.model.ConfigurationMergeConflictMapper
import me.moiclathy.edb.core.config.merge.model.ConfigurationMergeResult
import me.moiclathy.edb.core.config.snapshot.model.ConfigurationSnapshot
import me.moiclathy.edb.core.config.snapshot.service.ConfigurationSnapshotService
import me.moiclathy.edb.core.service.AuditService // 引入你的 AuditService
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings

class ConfigurationMergeService(
    private val snapshotService: ConfigurationSnapshotService,
    private val mergeEngine: MergeEngine,
    private val auditService: AuditService // 1. 注入审计服务
) {

    private val load = Load(LoadSettings.builder().build())
    private val dump = Dump(DumpSettings.builder().build())

    fun mergeSnapshots(
        targetSnapshotId: Long,
        sourceSnapshotId: Long,
        userId: Int, // 2. 传入操作用户 ID 以便记录审计
        strategy: MergeStrategy = MergeStrategy.FAIL_ON_CONFLICT
    ): ConfigurationMergeResult {

        validateSnapshotIds(targetSnapshotId, sourceSnapshotId)

        val targetSnapshot = loadSnapshot(targetSnapshotId)
        val sourceSnapshot = loadSnapshot(sourceSnapshotId)

        validateSnapshots(targetSnapshot, sourceSnapshot)

        val targetMap = parseYamlToMap(targetSnapshot.yaml)
        val sourceMap = parseYamlToMap(sourceSnapshot.yaml)

        val mergeRequest = MergeRequest(
            target = targetMap,
            source = sourceMap,
            strategy = strategy
        )

        val mergeResult = mergeEngine.merge(mergeRequest)

        // 3. 存在冲突时，记录冲突审计并返回
        if (mergeResult.conflicts.isNotEmpty()) {
            val conflicts = ConfigurationMergeConflictMapper.toModel(
                mergeResult.conflicts
            )

            // 接入审计：记录合并冲突
            auditService.record(
                userId = userId,
                action = "CONFIGURATION_MERGE_CONFLICT",
                resourceType = "CONFIGURATION_MERGE",
                resourceId = targetSnapshot.id?.toInt(),
                details = "Merge conflict: sourceSnapshot=${sourceSnapshot.id}, targetSnapshot=${targetSnapshot.id}, conflicts=${mergeResult.conflicts.size}"
            )

            return ConfigurationMergeResult.conflict(
                target = targetSnapshot,
                source = sourceSnapshot,
                conflicts = conflicts
            )
        }

        // 4. 无冲突，记录成功审计，序列化并返回
        val mergedYamlString = dump.dumpToString(mergeResult.merged)

        // 接入审计：记录合并成功
        auditService.record(
            userId = userId,
            action = "CONFIGURATION_MERGE",
            resourceType = "CONFIGURATION_MERGE",
            resourceId = targetSnapshot.id?.toInt(),
            details = "Merged snapshot ${sourceSnapshot.id} into ${targetSnapshot.id}"
        )

        return ConfigurationMergeResult.success(
            target = targetSnapshot,
            source = sourceSnapshot,
            yaml = mergedYamlString
        )
    }

    private fun parseYamlToMap(yaml: String): Map<String, Any?> {
        val root = load.loadFromString(yaml)
        @Suppress("UNCHECKED_CAST")
        return root as? Map<String, Any?>
            ?: throw SnapshotMergeException(
                code = "INVALID_YAML_FORMAT",
                message = "Configuration root must be a YAML object."
            )
    }

    private fun validateSnapshotIds(targetSnapshotId: Long, sourceSnapshotId: Long) {
        if (targetSnapshotId <= 0) {
            throw SnapshotMergeException("INVALID_TARGET_SNAPSHOT_ID", "Target snapshot ID must be greater than zero.")
        }
        if (sourceSnapshotId <= 0) {
            throw SnapshotMergeException("INVALID_SOURCE_SNAPSHOT_ID", "Source snapshot ID must be greater than zero.")
        }
        if (targetSnapshotId == sourceSnapshotId) {
            throw SnapshotMergeException("SNAPSHOT_SELF_MERGE", "A snapshot cannot be merged with itself.")
        }
    }

    private fun loadSnapshot(snapshotId: Long): ConfigurationSnapshot {
        return try {
            snapshotService.getSnapshot(snapshotId)
        } catch (exception: IllegalArgumentException) {
            throw SnapshotMergeException("SNAPSHOT_NOT_FOUND", "Configuration snapshot not found: $snapshotId", exception)
        }
    }

    private fun validateSnapshots(targetSnapshot: ConfigurationSnapshot, sourceSnapshot: ConfigurationSnapshot) {
        if (targetSnapshot.projectId != sourceSnapshot.projectId) {
            throw SnapshotMergeException("SNAPSHOT_PROJECT_MISMATCH", "Snapshots belong to different projects.")
        }
        if (targetSnapshot.yaml.isBlank()) {
            throw SnapshotMergeException("TARGET_SNAPSHOT_EMPTY", "Target snapshot contains empty YAML.")
        }
        if (sourceSnapshot.yaml.isBlank()) {
            throw SnapshotMergeException("SOURCE_SNAPSHOT_EMPTY", "Source snapshot contains empty YAML.")
        }
    }
}