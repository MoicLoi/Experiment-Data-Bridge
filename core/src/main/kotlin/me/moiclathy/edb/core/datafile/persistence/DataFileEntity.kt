package me.moiclathy.edb.core.datafile.persistence

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import java.time.Instant

data class DataFileEntity(
    val id: Int?,
    val projectId: Int,
    val createdByUserId: Int,
    val producingTaskId: Int?,
    val kind: DataFileKind,
    val format: DataFileFormat,
    val status: DataFileStatus,
    val originalFileName: String,
    val mediaType: String?,
    val sizeBytes: Long?,
    val sha256: String,
    val storageProvider: String,
    val storageKey: String,
    val origin: DataFileOrigin,
    val parentDataFileIds: Set<Int>,
    val externalSystem: String?,
    val metadataJson: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

