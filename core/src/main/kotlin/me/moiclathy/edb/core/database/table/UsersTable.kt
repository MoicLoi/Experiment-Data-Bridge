package me.moiclathy.edb.core.database.table

import me.moiclathy.edb.core.security.UserRole
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UsersTable : Table("users") {

    val id =
        integer("id")
            .autoIncrement()

    val username =
        varchar("username", 50)
            .uniqueIndex()

    val passwordHash =
        varchar("password_hash", 255)

    val fullName =
        varchar("full_name", 100)

    val email =
        varchar("email", 100)
            .nullable()
            .uniqueIndex()

    val employeeId =
        varchar("employee_id", 50)
            .nullable()
            .uniqueIndex()

    val role =
        enumerationByName(
            "role",
            20,
            UserRole::class
        )

    val isActive =
        bool("is_active")
            .default(true)

    val createdAt =
        timestamp("created_at")

    val updatedAt =
        timestamp("updated_at")

    override val primaryKey =
        PrimaryKey(id)
}