package me.moiclathy.edb.core.database.table

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object DataFilesTable : Table("data_files") {

    val id =
        integer("id")
            .autoIncrement()

    val taskId =
        reference(
            "task_id",
            TasksTable.id
        )
            .nullable()

    val projectId =
        reference(
            "project_id",
            ProjectsTable.id
        ).nullable()

    val kind = enumerationByName("kind", 30, DataFileKind::class)

    val format = enumerationByName("format", 20, DataFileFormat::class)

    val status = enumerationByName("status", 20, DataFileStatus::class)

    val fileName =
        varchar("file_name", 255)

    val filePath =
        varchar("file_path", 500)

    val fileHash =
        varchar("file_hash", 64)

    val mediaType = varchar("media_type", 255).nullable()

    val sizeBytes = long("size_bytes").nullable()

    val storageProvider = varchar("storage_provider", 50)

    val storageKey = varchar("storage_key", 500)

    val origin = enumerationByName("origin", 30, DataFileOrigin::class)

    val externalSystem = varchar("external_system", 100).nullable()

    val metadataJson = text("metadata_json")

    val uploadedBy =
        reference(
            "uploaded_by",
            UsersTable.id
        )

    val uploadedAt =
        timestamp("uploaded_at")

    val updatedAt = timestamp("updated_at")

    override val primaryKey =
        PrimaryKey(id)
}

object DataFileParentsTable : Table("data_file_parents") {
    val childDataFileId = reference("child_data_file_id", DataFilesTable.id)
    val parentDataFileId = reference("parent_data_file_id", DataFilesTable.id)

    override val primaryKey = PrimaryKey(childDataFileId, parentDataFileId)
}
