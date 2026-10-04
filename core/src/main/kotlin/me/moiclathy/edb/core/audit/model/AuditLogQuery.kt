package me.moiclathy.edb.core.audit.model

import java.time.Instant

/**
 * Audit 查询条件。
 *
 * 所有字段均为可选条件。
 *
 * 未提供的字段不会参与过滤。
 */
data class AuditLogQuery(

    /**
     * 按用户 ID 过滤。
     */
    val userId: Int? = null,

    /**
     * 按操作类型过滤。
     */
    val action: String? = null,

    /**
     * 按资源类型过滤。
     */
    val resourceType: String? = null,

    /**
     * 按资源 ID 过滤。
     */
    val resourceId: Int? = null,

    /**
     * 查询开始时间。
     *
     * 包含该时间。
     */
    val from: Instant? = null,

    /**
     * 查询结束时间。
     *
     * 包含该时间。
     */
    val to: Instant? = null,

    /**
     * 页码。
     *
     * 从 0 开始。
     */
    val page: Int = 0,

    /**
     * 每页数量。
     */
    val pageSize: Int = 20
) {

    init {
        require(page >= 0) {
            "Page must be greater than or equal to zero."
        }

        require(pageSize in 1..100) {
            "Page size must be between 1 and 100."
        }

        if (from != null && to != null) {
            require(!from.isAfter(to)) {
                "Query start time must not be after end time."
            }
        }
    }

    val offset: Long
        get() = page.toLong() * pageSize.toLong()
}