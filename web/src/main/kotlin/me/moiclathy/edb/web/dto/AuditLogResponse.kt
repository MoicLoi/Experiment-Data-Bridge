package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.database.model.AuditLog
import java.time.Instant

@Serializable
data class AuditLogResponse(
    val id: Long,
    val userId: Int,
    val action: String,
    val resourceType: String,
    val resourceId: Int?,
    val details: String?,
    val createdAt: String
) {

    companion object {

        fun from(
            auditLog: AuditLog
        ): AuditLogResponse {

            return AuditLogResponse(
                id = auditLog.id,
                userId = auditLog.userId,
                action = auditLog.action,
                resourceType = auditLog.resourceType,
                resourceId = auditLog.resourceId,
                details = auditLog.details,
                createdAt = auditLog.createdAt.toString()
            )
        }
    }
}