package me.moiclathy.edb.core.config.snapshot.persistence

import me.moiclathy.edb.core.config.snapshot.model.ConfigurationSnapshot
import me.moiclathy.edb.core.config.snapshot.model.SnapshotMetadata

object ConfigurationSnapshotMapper {

    fun toEntity(
        snapshot: ConfigurationSnapshot
    ): ConfigurationSnapshotEntity {

        return ConfigurationSnapshotEntity(
            id = snapshot.id,
            projectId = snapshot.projectId,
            version = snapshot.version,
            createdAt = snapshot.metadata.createdAt,
            createdBy = snapshot.metadata.createdBy,
            description = snapshot.metadata.description,
            yaml = snapshot.yaml
        )
    }

    fun toModel(
        entity: ConfigurationSnapshotEntity
    ): ConfigurationSnapshot {

        return ConfigurationSnapshot(
            id = entity.id,
            projectId = entity.projectId,
            version = entity.version,
            metadata = SnapshotMetadata(
                createdAt = entity.createdAt,
                createdBy = entity.createdBy,
                description = entity.description
            ),
            yaml = entity.yaml
        )
    }
}