package me.moiclathy.edb.core.database.table

import me.moiclathy.edb.core.datafile.upload.ResumableUploadStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object DataFileUploadSessionsTable:Table("data_file_upload_sessions"){
    val id=varchar("id",36)
    val projectId=reference("project_id",ProjectsTable.id)
    val userId=reference("user_id",UsersTable.id)
    val fileName=varchar("file_name",255)
    val mediaType=varchar("media_type",255).nullable()
    val totalBytes=long("total_bytes")
    val offsetBytes=long("offset_bytes")
    val status=enumerationByName("status",20,ResumableUploadStatus::class)
    val createdAt=timestamp("created_at")
    val updatedAt=timestamp("updated_at")
    override val primaryKey=PrimaryKey(id)
}
