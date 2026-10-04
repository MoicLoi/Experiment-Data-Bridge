package me.moiclathy.edb.core.database.table



import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object ConfigurationSnapshotTable : LongIdTable(
    name = "edb_configuration_snapshots"
) {

    val projectId =
        integer("project_id")

    val version =
        integer("version")

    val createdAt =
        timestamp("created_at")

    val createdBy =
        varchar(
            "created_by",
            255
        )

    val description =
        varchar(
            "description",
            1000
        ).nullable()

    val yaml =
        text("yaml")
}