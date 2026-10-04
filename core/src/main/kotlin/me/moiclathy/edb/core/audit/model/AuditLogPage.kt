package me.moiclathy.edb.core.audit.model

import me.moiclathy.edb.core.database.model.AuditLog

/**
 * Audit 查询分页结果。
 */
data class AuditLogPage(

    /**
     * 当前页数据。
     */
    val items: List<AuditLog>,

    /**
     * 当前页码。
     */
    val page: Int,

    /**
     * 每页数量。
     */
    val pageSize: Int,

    /**
     * 符合查询条件的总记录数。
     */
    val total: Long
) {

    /**
     * 是否存在下一页。
     */
    val hasNext: Boolean
        get() = (page + 1L) * pageSize < total

    /**
     * 是否存在上一页。
     */
    val hasPrevious: Boolean
        get() = page > 0
}