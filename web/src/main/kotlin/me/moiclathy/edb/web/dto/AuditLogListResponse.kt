package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.audit.model.AuditLogPage

@Serializable
data class AuditLogListResponse(
    val items: List<AuditLogResponse>,
    val total: Long
) {

    companion object {

        fun from(
            page: AuditLogPage
        ): AuditLogListResponse {

            return AuditLogListResponse(
                items = page.items.map {
                    AuditLogResponse.from(it)
                },
                total = page.total
            )
        }
    }
}