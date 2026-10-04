package me.moiclathy.edb.core.config.compare.service

import me.moiclathy.edb.core.config.compare.model.ConfigurationCompareChange
import me.moiclathy.edb.core.config.compare.model.ConfigurationCompareChangeType
import me.moiclathy.edb.core.config.compare.model.ConfigurationCompareResult
import me.moiclathy.edb.core.config.snapshot.model.ConfigurationSnapshot
import me.moiclathy.edb.core.config.snapshot.repository.ConfigurationSnapshotRepository
import me.moiclathy.edb.core.service.AuditService
import org.yaml.snakeyaml.Yaml

class ConfigurationCompareService(
    private val snapshotRepository: ConfigurationSnapshotRepository,
    private val auditService: AuditService
) {

    private val yaml = Yaml()

    fun compare(
        sourceSnapshotId: Long,
        targetSnapshotId: Long,
        userId: Int
    ): ConfigurationCompareResult {

        validateSnapshotIds(
            sourceSnapshotId,
            targetSnapshotId
        )

        val sourceSnapshot =
            loadSnapshot(sourceSnapshotId)

        val targetSnapshot =
            loadSnapshot(targetSnapshotId)



        validateSnapshots(
            sourceSnapshot,
            targetSnapshot
        )

        val source =
            parseYaml(sourceSnapshot.yaml)

        val target =
            parseYaml(targetSnapshot.yaml)

        val changes =
            mutableListOf<ConfigurationCompareChange>()



        compareValues(
            path = "",
            sourceValue = source,
            targetValue = target,
            changes = changes
        )

        /*return ConfigurationCompareResult(
            sourceSnapshot = sourceSnapshot,
            targetSnapshot = targetSnapshot,
            changes = changes
        )*/

        val result = ConfigurationCompareResult(
            sourceSnapshot = sourceSnapshot,
            targetSnapshot = targetSnapshot,
            changes = changes
        )

        // 2. 差异计算完成后，再接入审计，此时 result.changes.size 才是正确的统计数字
        auditService.record(
            userId = userId,
            action = "CONFIGURATION_COMPARE",
            resourceType = "CONFIGURATION_COMPARE",
            resourceId = targetSnapshotId.toInt(),
            details = "Compared snapshot $targetSnapshotId with $sourceSnapshotId, differences=${result.changes.size}"
        )

        // 3. 返回最终结果
        return result

    }

    private fun validateSnapshotIds(
        sourceSnapshotId: Long,
        targetSnapshotId: Long
    ) {

        require(sourceSnapshotId > 0) {
            "Source snapshot ID must be greater than zero."
        }

        require(targetSnapshotId > 0) {
            "Target snapshot ID must be greater than zero."
        }

        require(sourceSnapshotId != targetSnapshotId) {
            "Source and target snapshot IDs must be different."
        }
    }

    private fun loadSnapshot(
        id: Long
    ): ConfigurationSnapshot {

        return snapshotRepository.findById(id)
            ?: throw IllegalArgumentException(
                "Configuration snapshot not found: $id"
            )
    }

    private fun validateSnapshots(
        source: ConfigurationSnapshot,
        target: ConfigurationSnapshot
    ) {

        require(source.projectId == target.projectId) {
            "Source and target snapshots must belong to the same project."
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseYaml(
        yamlContent: String
    ): Map<String, Any?> {

        require(yamlContent.isNotBlank()) {
            "Snapshot YAML must not be blank."
        }

        val parsed =
            yaml.load<Any?>(yamlContent)

        return when (parsed) {

            null ->
                emptyMap()

            is Map<*, *> ->
                parsed.entries.associate {
                    it.key.toString() to it.value
                }

            else ->
                throw IllegalArgumentException(
                    "Snapshot YAML root must be a mapping."
                )
        }
    }

    private fun compareValues(
        path: String,
        sourceValue: Any?,
        targetValue: Any?,
        changes: MutableList<ConfigurationCompareChange>
    ) {

        /*
         * 两边都是 Map：
         *
         * 继续递归比较。
         */
        if (
            sourceValue is Map<*, *> &&
            targetValue is Map<*, *>
        ) {

            val keys =
                LinkedHashSet<Any?>()

            keys.addAll(sourceValue.keys)
            keys.addAll(targetValue.keys)

            for (key in keys) {

                val childPath =
                    if (path.isBlank()) {
                        key.toString()
                    } else {
                        "$path.${key}"
                    }

                compareValues(
                    path = childPath,
                    sourceValue = sourceValue[key],
                    targetValue = targetValue[key],
                    changes = changes
                )
            }

            return
        }

        /*
         * source 中存在、target 中不存在。
         */
        if (
            sourceValue != null &&
            targetValue == null
        ) {

            changes += ConfigurationCompareChange(
                path = path,
                type = ConfigurationCompareChangeType.REMOVED,
                sourceValue = sourceValue,
                targetValue = null
            )

            return
        }

        /*
         * source 中不存在、target 中存在。
         */
        if (
            sourceValue == null &&
            targetValue != null
        ) {

            changes += ConfigurationCompareChange(
                path = path,
                type = ConfigurationCompareChangeType.ADDED,
                sourceValue = null,
                targetValue = targetValue
            )

            return
        }

        /*
         * 两边都为 null。
         */
        if (
            sourceValue == null &&
            targetValue == null
        ) {
            return
        }

        /*
         * List 不做运行时 Class 比较。
         *
         * 与 0.23.9 MergeEngine 的修复原则保持一致：
         * List 必须优先作为集合处理。
         */
        if (
            sourceValue is List<*> &&
            targetValue is List<*>
        ) {

            if (sourceValue != targetValue) {

                changes += ConfigurationCompareChange(
                    path = path,
                    type = ConfigurationCompareChangeType.CHANGED,
                    sourceValue = sourceValue,
                    targetValue = targetValue
                )
            }

            return
        }

        /*
         * 普通叶子节点。
         */
        if (sourceValue != targetValue) {

            changes += ConfigurationCompareChange(
                path = path,
                type = ConfigurationCompareChangeType.CHANGED,
                sourceValue = sourceValue,
                targetValue = targetValue
            )
        }
    }
}