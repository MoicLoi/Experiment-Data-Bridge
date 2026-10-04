package me.moiclathy.edb.core.datafile.persistence

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStorageReference

class DataFileMapper(
    private val metadataCodec: DataFileMetadataJsonCodec = DataFileMetadataJsonCodec()
) {
    fun toEntity(dataFile: DataFile): DataFileEntity {
        val storage = requireNotNull(dataFile.storage) {
            "A persisted DataFile must have a storage reference."
        }
        val sha256 = requireNotNull(dataFile.sha256) {
            "A persisted DataFile must have a SHA-256 value."
        }
        return DataFileEntity(
            id = dataFile.id,
            projectId = dataFile.projectId,
            createdByUserId = dataFile.createdByUserId,
            producingTaskId = dataFile.provenance.producingTaskId,
            kind = dataFile.kind,
            format = dataFile.format,
            status = dataFile.status,
            originalFileName = dataFile.originalFileName,
            mediaType = dataFile.mediaType,
            sizeBytes = dataFile.sizeBytes,
            sha256 = sha256,
            storageProvider = storage.provider,
            storageKey = storage.key,
            origin = dataFile.provenance.origin,
            parentDataFileIds = dataFile.provenance.parentDataFileIds,
            externalSystem = dataFile.provenance.externalSystem,
            metadataJson = metadataCodec.encode(dataFile.metadata),
            createdAt = dataFile.createdAt,
            updatedAt = dataFile.updatedAt
        )
    }

    fun toModel(entity: DataFileEntity): DataFile = DataFile(
        id = entity.id,
        projectId = entity.projectId,
        createdByUserId = entity.createdByUserId,
        kind = entity.kind,
        format = entity.format,
        status = entity.status,
        originalFileName = entity.originalFileName,
        mediaType = entity.mediaType,
        sizeBytes = entity.sizeBytes,
        sha256 = entity.sha256,
        storage = DataFileStorageReference(entity.storageProvider, entity.storageKey),
        provenance = DataFileProvenance(
            origin = entity.origin,
            parentDataFileIds = entity.parentDataFileIds,
            producingTaskId = entity.producingTaskId,
            externalSystem = entity.externalSystem
        ),
        metadata = metadataCodec.decode(entity.metadataJson),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}

