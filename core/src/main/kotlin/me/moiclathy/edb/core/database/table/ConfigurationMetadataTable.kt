package me.moiclathy.edb.core.database.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Configuration Metadata 数据库表。
 *
 * 一个 project 对应一份 metadata。
 */
object ConfigurationMetadataTable : Table("configuration_metadata") {

    val projectId =
        integer("project_id")

    val description =
        text("description")
            .nullable()

    val category =
        varchar(
            "category",
            length = 128
        ).nullable()

    /**
     * JSON 字符串。
     *
     * 例如：
     * ["animal","experiment","veterinary"]
     */
    val tags =
        text("tags")

    val updatedAt =
        timestamp(
            "updated_at"
        )

    val dataSource =
        varchar(
            "source",
            length = 128
        ).nullable()

    override val primaryKey =
        PrimaryKey(projectId)
}