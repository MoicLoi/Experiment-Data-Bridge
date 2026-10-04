package me.moiclathy.edb.core.audit.repository


import me.moiclathy.edb.core.audit.model.AuditLogPage
import me.moiclathy.edb.core.audit.model.AuditLogQuery
import me.moiclathy.edb.core.database.model.AuditLog

/**
 * Audit 查询 Repository。
 *
 * 与现有 AuditRepository 的职责区别：
 *
 * AuditRepository
 *     → 写入审计日志
 *
 * AuditLogRepository
 *     → 查询审计日志
 */
interface AuditLogRepository {

    /**
     * 根据查询条件分页查询审计日志。
     */
    fun search(
        query: AuditLogQuery
    ): AuditLogPage

    /**
     * 根据 ID 查询单条审计日志。
     */
    fun findById(
        id: Long
    ): AuditLog?

    /**
     * 查询符合条件的日志数量。
     *
     * 通常用于分页。
     */
    fun count(
        query: AuditLogQuery
    ): Long
}