package me.moiclathy.edb.core.database.table


import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp


object TemplateTable : Table("templates") {

    val id =
        long("id")
            .autoIncrement()

    val name =
        varchar(
            "name",
            255
        )

    val description =
        text(
            "description"
        )

    val type =
        varchar(
            "type",
            64
        )

    val visibility =
        varchar(
            "visibility",
            64
        )

    val version =
        integer(
            "version"
        )

    val author =
        varchar(
            "author",
            128
        )

    val createdAt =
        timestamp(
            "created_at"
        )

    val updatedAt =
        timestamp(
            "updated_at"
        )

    val yaml =
        text(
            "yaml"
        )


    override val primaryKey =
        PrimaryKey(id)
}