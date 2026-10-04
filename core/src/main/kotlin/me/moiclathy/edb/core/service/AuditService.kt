package me.moiclathy.edb.core.service

import me.moiclathy.edb.core.audit.model.AuditLogPage
import me.moiclathy.edb.core.audit.model.AuditLogQuery
import me.moiclathy.edb.core.audit.repository.AuditLogRepository
import me.moiclathy.edb.core.database.model.AuditLog
import me.moiclathy.edb.core.database.repository.AuditRepository

/**
 * 审计服务。
 *
 * AuditService 是系统审计能力的统一业务入口。
 */
class AuditService(
    private val repository: AuditRepository,
    private val auditLogRepository: AuditLogRepository
) {

    /**
     * 记录一条审计日志。
     */
    fun record(
        userId: Int,
        action: String,
        resourceType: String,
        resourceId: Int?,
        details: String? = null
    ) {
        require(userId > 0) { "User ID must be greater than zero." }
        require(action.isNotBlank()) { "Audit action must not be blank." }
        require(resourceType.isNotBlank()) { "Audit resource type must not be blank." }
        if (resourceId != null) {
            require(resourceId > 0) { "Audit resource ID must be greater than zero." }
        }

        repository.save(
            userId = userId,
            action = action,
            resourceType = resourceType,
            resourceId = resourceId,
            details = details
        )
    }

    /**
     * 查询审计日志（提供默认查询条件，方便直接调用）。
     */
    fun search(
        query: AuditLogQuery = AuditLogQuery()
    ): AuditLogPage {
        return auditLogRepository.search(query)
    }

    /**
     * 根据 ID 查询单条审计日志。
     */
    fun get(
        id: Long
    ): AuditLog? {
        require(id > 0) { "Audit log ID must be greater than zero." }
        return auditLogRepository.findById(id)
    }

    /**
     * 根据 ID 查询审计日志。找不到时抛出异常。
     */
    fun require(
        id: Long
    ): AuditLog {
        return get(id) ?: throw IllegalArgumentException("Audit log not found: $id")
    }

    /**
     * 统计符合查询条件的审计日志数量。
     */
    fun count(
        query: AuditLogQuery = AuditLogQuery()
    ): Long {
        return auditLogRepository.count(query)
    }

    // -------------------------------------------------------------------------
    // Snapshot 业务审计方法
    // -------------------------------------------------------------------------

    fun recordSnapshotCreate(snapshotId: Long, projectId: Int, userId: Int) {
        require(snapshotId > 0) { "Snapshot ID must be greater than zero." }
        require(projectId > 0) { "Project ID must be greater than zero." }

        record(
            userId = userId,
            action = "SNAPSHOT_CREATE",
            resourceType = "SNAPSHOT",
            resourceId = snapshotId.toInt(),
            details = "Created configuration snapshot for project $projectId"
        )
    }

    fun recordSnapshotView(snapshotId: Long, userId: Int) {
        require(snapshotId > 0) { "Snapshot ID must be greater than zero." }
        record(userId = userId, action = "SNAPSHOT_VIEW", resourceType = "SNAPSHOT", resourceId = snapshotId.toInt())
    }

    fun recordSnapshotDelete(snapshotId: Long, userId: Int) {
        require(snapshotId > 0) { "Snapshot ID must be greater than zero." }
        record(userId = userId, action = "SNAPSHOT_DELETE", resourceType = "SNAPSHOT", resourceId = snapshotId.toInt())
    }

    // -------------------------------------------------------------------------
    // Configuration 业务审计方法
    // -------------------------------------------------------------------------

    fun recordConfigurationMerge(projectId: Int, userId: Int, targetSnapshotId: Long, sourceSnapshotId: Long, success: Boolean) {
        require(projectId > 0) { "Project ID must be greater than zero." }
        record(
            userId = userId,
            action = "CONFIGURATION_MERGE",
            resourceType = "CONFIGURATION",
            resourceId = projectId,
            details = "targetSnapshotId=$targetSnapshotId,sourceSnapshotId=$sourceSnapshotId,success=$success"
        )
    }

    fun recordConfigurationCompare(projectId: Int, userId: Int, targetSnapshotId: Long, sourceSnapshotId: Long) {
        require(projectId > 0) { "Project ID must be greater than zero." }
        record(
            userId = userId,
            action = "CONFIGURATION_COMPARE",
            resourceType = "CONFIGURATION",
            resourceId = projectId,
            details = "targetSnapshotId=$targetSnapshotId,sourceSnapshotId=$sourceSnapshotId"
        )
    }

    fun recordConfigurationExport(projectId: Int, userId: Int) {
        require(projectId > 0) { "Project ID must be greater than zero." }
        record(userId = userId, action = "CONFIGURATION_EXPORT", resourceType = "CONFIGURATION", resourceId = projectId)
    }

    fun recordConfigurationImport(projectId: Int, userId: Int) {
        require(projectId > 0) { "Project ID must be greater than zero." }
        record(userId = userId, action = "CONFIGURATION_IMPORT", resourceType = "CONFIGURATION", resourceId = projectId)
    }
}