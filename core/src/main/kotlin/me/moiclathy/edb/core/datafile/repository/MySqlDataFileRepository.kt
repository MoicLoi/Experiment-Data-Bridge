package me.moiclathy.edb.core.datafile.repository

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.persistence.DataFileEntity
import me.moiclathy.edb.core.datafile.persistence.DataFileMapper
import me.moiclathy.edb.core.database.table.DataFileParentsTable
import me.moiclathy.edb.core.database.table.DataFilesTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class MySqlDataFileRepository(
    private val mapper: DataFileMapper = DataFileMapper()
) : DataFileRepository, DataFileStorageReferenceIndex {
    override fun save(dataFile: DataFile): DataFile {
        require(dataFile.id == null) { "A new DataFile must not already have an id." }
        val entity = mapper.toEntity(dataFile)
        return transaction {
            val id = DataFilesTable.insert { row -> row.write(entity) }
                .resultedValues
                ?.single()
                ?.get(DataFilesTable.id)
                ?: error("Failed to create DataFile")
            insertParents(id, entity.parentDataFileIds)
            dataFile.copy(id = id)
        }
    }

    override fun findById(id: Int): DataFile? {
        require(id > 0) { "DataFile id must be positive." }
        return transaction {
            DataFilesTable.selectAll()
                .where { DataFilesTable.id eq id }
                .limit(1)
                .singleOrNull()
                ?.toDataFile()
        }
    }

    override fun findByProjectId(projectId: Int): List<DataFile> {
        require(projectId > 0) { "Project id must be positive." }
        return transaction {
            DataFilesTable.selectAll()
                .where { DataFilesTable.projectId eq projectId }
                .orderBy(
                    DataFilesTable.uploadedAt to SortOrder.DESC,
                    DataFilesTable.id to SortOrder.DESC
                )
                .map { it.toDataFile() }
        }
    }

    override fun update(dataFile: DataFile): DataFile {
        val id = requireNotNull(dataFile.id) { "A persisted DataFile must have an id." }
        val entity = mapper.toEntity(dataFile)
        return transaction {
            val updated = DataFilesTable.update(where = { DataFilesTable.id eq id }) { row ->
                row.write(entity)
            }
            require(updated == 1) { "DataFile not found: $id" }
            DataFileParentsTable.deleteWhere { childDataFileId eq id }
            insertParents(id, entity.parentDataFileIds)
            dataFile
        }
    }

    override fun findRetainedStorageKeys(provider: String): Set<String> {
        require(provider.isNotBlank()) { "Storage provider must not be blank." }
        return transaction {
            DataFilesTable
                .select(DataFilesTable.storageKey)
                .where {
                    (DataFilesTable.storageProvider eq provider) and
                        (DataFilesTable.status neq DataFileStatus.DELETED)
                }
                .mapTo(mutableSetOf()) { it[DataFilesTable.storageKey] }
        }
    }

    private fun org.jetbrains.exposed.sql.statements.UpdateBuilder<*>.write(entity: DataFileEntity) {
        this[DataFilesTable.projectId] = entity.projectId
        this[DataFilesTable.taskId] = entity.producingTaskId
        this[DataFilesTable.kind] = entity.kind
        this[DataFilesTable.format] = entity.format
        this[DataFilesTable.status] = entity.status
        this[DataFilesTable.fileName] = entity.originalFileName
        this[DataFilesTable.filePath] = entity.storageKey
        this[DataFilesTable.fileHash] = entity.sha256
        this[DataFilesTable.mediaType] = entity.mediaType
        this[DataFilesTable.sizeBytes] = entity.sizeBytes
        this[DataFilesTable.storageProvider] = entity.storageProvider
        this[DataFilesTable.storageKey] = entity.storageKey
        this[DataFilesTable.origin] = entity.origin
        this[DataFilesTable.externalSystem] = entity.externalSystem
        this[DataFilesTable.metadataJson] = entity.metadataJson
        this[DataFilesTable.uploadedBy] = entity.createdByUserId
        this[DataFilesTable.uploadedAt] = entity.createdAt
        this[DataFilesTable.updatedAt] = entity.updatedAt
    }

    private fun insertParents(childId: Int, parentIds: Set<Int>) {
        parentIds.forEach { parentId ->
            DataFileParentsTable.insert { row ->
                row[childDataFileId] = childId
                row[parentDataFileId] = parentId
            }
        }
    }

    private fun ResultRow.toDataFile(): DataFile {
        val id = this[DataFilesTable.id]
        val parentIds = DataFileParentsTable.selectAll()
            .where { DataFileParentsTable.childDataFileId eq id }
            .mapTo(mutableSetOf()) { it[DataFileParentsTable.parentDataFileId] }
        return mapper.toModel(
            DataFileEntity(
                id = id,
                projectId = requireNotNull(this[DataFilesTable.projectId]) {
                    "Legacy DataFile $id has no project id."
                },
                createdByUserId = this[DataFilesTable.uploadedBy],
                producingTaskId = this[DataFilesTable.taskId],
                kind = this[DataFilesTable.kind],
                format = this[DataFilesTable.format],
                status = this[DataFilesTable.status],
                originalFileName = this[DataFilesTable.fileName],
                mediaType = this[DataFilesTable.mediaType],
                sizeBytes = this[DataFilesTable.sizeBytes],
                sha256 = this[DataFilesTable.fileHash],
                storageProvider = this[DataFilesTable.storageProvider],
                storageKey = this[DataFilesTable.storageKey],
                origin = this[DataFilesTable.origin],
                parentDataFileIds = parentIds,
                externalSystem = this[DataFilesTable.externalSystem],
                metadataJson = this[DataFilesTable.metadataJson],
                createdAt = this[DataFilesTable.uploadedAt],
                updatedAt = this[DataFilesTable.updatedAt]
            )
        )
    }
}
