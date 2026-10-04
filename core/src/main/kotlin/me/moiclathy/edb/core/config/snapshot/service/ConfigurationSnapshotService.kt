package me.moiclathy.edb.core.config.snapshot.service

import me.moiclathy.edb.core.config.snapshot.model.ConfigurationSnapshot
import me.moiclathy.edb.core.config.snapshot.model.SnapshotMetadata
import me.moiclathy.edb.core.config.snapshot.repository.ConfigurationSnapshotRepository
import me.moiclathy.edb.core.service.AuditService
import java.time.Instant

class ConfigurationSnapshotService(
    private val repository: ConfigurationSnapshotRepository,
    private val auditService: AuditService
) {

    fun createSnapshot(
        projectId: Int,
        yaml: String,
        createdBy: String,
        createdByUserId: Int,
        description: String? = null
    ): ConfigurationSnapshot {

        require(projectId > 0) {
            "Project ID must be greater than zero."
        }

        require(yaml.isNotBlank()) {
            "Snapshot YAML must not be blank."
        }

        require(createdBy.isNotBlank()) {
            "Snapshot creator must not be blank."
        }
        require(createdByUserId > 0) { "Snapshot creator user ID must be greater than zero." }

        val latest =
            repository.findLatest(projectId)

        val nextVersion =
            (latest?.version ?: 0) + 1

        val snapshot = ConfigurationSnapshot(
            id = null,
            projectId = projectId,
            version = nextVersion,
            metadata = SnapshotMetadata(
                createdAt = Instant.now(),
                createdBy = createdBy,
                description = description
            ),
            yaml = yaml
        )

        val saved = repository.save(snapshot)

        saved.id?.let { snapshotId ->
            auditService.recordSnapshotCreate(
                snapshotId = snapshotId,          // 直接用 let 作用域里解出来的 Long
                projectId = projectId,
                userId = createdByUserId
            )
        }

        return saved
    }

    fun getSnapshot(
        id: Long
    ): ConfigurationSnapshot {

        require(id > 0) {
            "Snapshot ID must be greater than zero."
        }

        return repository.findById(id)
            ?: throw IllegalArgumentException(
                "Configuration snapshot not found: $id"
            )
    }

    fun getSnapshotOrNull(
        id: Long
    ): ConfigurationSnapshot? {

        if (id <= 0) {
            return null
        }

        return repository.findById(id)
    }

    fun getLatestSnapshot(
        projectId: Int
    ): ConfigurationSnapshot? {

        require(projectId > 0) {
            "Project ID must be greater than zero."
        }

        return repository.findLatest(projectId)
    }

    fun listSnapshots(
        projectId: Int
    ): List<ConfigurationSnapshot> {

        require(projectId > 0) {
            "Project ID must be greater than zero."
        }

        return repository.findByProject(projectId)
    }

    fun deleteSnapshot(
        id: Long,
        userId: Int
    ) {

        require(id > 0) {
            "Snapshot ID must be greater than zero."
        }

        repository.delete(id)

        auditService.recordSnapshotDelete(
            snapshotId = id,
            userId = userId
        )
    }

    fun countSnapshots(
        projectId: Int
    ): Long {

        require(projectId > 0) {
            "Project ID must be greater than zero."
        }

        return repository.count(projectId)
    }
}
