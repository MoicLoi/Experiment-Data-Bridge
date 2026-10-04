package me.moiclathy.edb.core.database.table

import me.moiclathy.edb.core.project.ProjectMode
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ProjectsTable : Table("projects") {

    val id =
        integer("id")
            .autoIncrement()

    val name =
        varchar("name", 200)

    val description =
        text("description")
            .nullable()

    val ownerId =
        integer("owner_id")

    val mode =
        enumerationByName(
            "mode",
            20,
            ProjectMode::class
        )

    val configYaml =
        text("config_yaml")
            .nullable()

    val createdAt =
        timestamp("created_at")

    val updatedAt =
        timestamp("updated_at")

    override val primaryKey =
        PrimaryKey(id)
}