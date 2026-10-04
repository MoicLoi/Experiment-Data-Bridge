package me.moiclathy.edb.core.database.table

import me.moiclathy.edb.core.audit.AuditStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object AuditLogsTable : Table("audit_logs") {

    val id =
        long("id")
            .autoIncrement()

    val userId =
        reference(
            "user_id",
            UsersTable.id
        )

    val action =
        varchar("action", 100)

    val resourceType =
        varchar("resource_type", 50)
            .nullable()

    val resourceId =
        integer("resource_id")
            .nullable()

    val details =
        text("details")
            .nullable()

    val ipAddress =
        varchar("ip_address", 45)
            .nullable()

    val userAgent =
        varchar("user_agent", 255)
            .nullable()

    val status =
        enumerationByName(
            "status",
            20,
            AuditStatus::class
        )

    val createdAt =
        timestamp("created_at")

    override val primaryKey =
        PrimaryKey(id)
}