package me.moiclathy.edb.core.datafile.serialization

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.model.DataFileStorageReference
import java.time.Instant

data class DataFileDescriptor(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: Int? = null,
    val projectId: Int,
    val createdByUserId: Int,
    val kind: DataFileKind,
    val format: DataFileFormat,
    val status: DataFileStatus,
    val originalFileName: String,
    val mediaType: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val storage: DataFileStorageReference? = null,
    val provenance: DataFileProvenance,
    val metadata: DataFileMetadata = DataFileMetadata.EMPTY,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported DataFile descriptor schema version: $schemaVersion"
        }
    }

    fun toDataFile(): DataFile = DataFile(
        id = id,
        projectId = projectId,
        createdByUserId = createdByUserId,
        kind = kind,
        format = format,
        status = status,
        originalFileName = originalFileName,
        mediaType = mediaType,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        storage = storage,
        provenance = provenance,
        metadata = metadata,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun from(dataFile: DataFile): DataFileDescriptor = DataFileDescriptor(
            id = dataFile.id,
            projectId = dataFile.projectId,
            createdByUserId = dataFile.createdByUserId,
            kind = dataFile.kind,
            format = dataFile.format,
            status = dataFile.status,
            originalFileName = dataFile.originalFileName,
            mediaType = dataFile.mediaType,
            sizeBytes = dataFile.sizeBytes,
            sha256 = dataFile.sha256,
            storage = dataFile.storage,
            provenance = dataFile.provenance,
            metadata = dataFile.metadata,
            createdAt = dataFile.createdAt,
            updatedAt = dataFile.updatedAt
        )
    }
}
