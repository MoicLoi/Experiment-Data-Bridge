package me.moiclathy.edb.core.datafile.application

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.DataFileStorage
import me.moiclathy.edb.core.datafile.lifecycle.DataFileDeletionService
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.upload.DataFileUploadRequest
import me.moiclathy.edb.core.datafile.upload.DataFileUploadService
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.core.exception.PermissionDeniedException
import me.moiclathy.edb.core.exception.ProjectNotFoundException
import me.moiclathy.edb.core.security.PermissionChecker
import java.io.InputStream

data class UploadDataFileCommand(
    val projectId: Int,
    val userId: Int,
    val originalFileName: String,
    val input: InputStream,
    val declaredMediaType: String? = null,
    val metadata: DataFileMetadata = DataFileMetadata.EMPTY
)

data class DataFileDownload(
    val dataFile: DataFile,
    val input: InputStream
)

class DataFileNotFoundException(id: Int) : EDBException(
    code = "DATA_FILE_NOT_FOUND",
    message = "DataFile $id not found"
)

class DataFileApplicationService(
    private val uploadService: DataFileUploadService,
    private val repository: DataFileRepository,
    private val storage: DataFileStorage,
    private val projectRepository: ProjectRepository,
    private val permissionChecker: PermissionChecker,
    private val deletionService: DataFileDeletionService = DataFileDeletionService(repository, storage)
) {
    fun upload(command: UploadDataFileCommand): DataFile {
        val project = projectRepository.findById(command.projectId)
            ?: throw ProjectNotFoundException(command.projectId)
        if (!permissionChecker.requireWritable(command.userId, project)) {
            throw PermissionDeniedException()
        }
        return uploadService.upload(
            DataFileUploadRequest(
                projectId = command.projectId,
                uploadedByUserId = command.userId,
                originalFileName = command.originalFileName,
                input = command.input,
                declaredMediaType = command.declaredMediaType,
                metadata = command.metadata
            )
        )
    }

    fun list(projectId: Int, userId: Int): List<DataFile> {
        requireReadableProject(projectId, userId)
        return repository.findByProjectId(projectId)
    }

    fun get(projectId: Int, dataFileId: Int, userId: Int): DataFile {
        requireReadableProject(projectId, userId)
        val dataFile = repository.findById(dataFileId)
            ?: throw DataFileNotFoundException(dataFileId)
        if (dataFile.projectId != projectId) throw DataFileNotFoundException(dataFileId)
        return dataFile
    }

    fun openDownload(projectId: Int, dataFileId: Int, userId: Int): DataFileDownload {
        val dataFile = get(projectId, dataFileId, userId)
        if (dataFile.status == DataFileStatus.DELETED) throw DataFileNotFoundException(dataFileId)
        val storageKey = dataFile.storage?.key ?: throw DataFileNotFoundException(dataFileId)
        return DataFileDownload(dataFile, storage.open(storageKey))
    }

    fun delete(projectId: Int, dataFileId: Int, userId: Int): DataFile {
        val project = projectRepository.findById(projectId)
            ?: throw ProjectNotFoundException(projectId)
        if (!permissionChecker.requireWritable(userId, project)) throw PermissionDeniedException()
        return deletionService.delete(projectId, dataFileId)
    }

    private fun requireReadableProject(projectId: Int, userId: Int) {
        val project = projectRepository.findById(projectId)
            ?: throw ProjectNotFoundException(projectId)
        if (!permissionChecker.requireReadable(userId, project)) {
            throw PermissionDeniedException()
        }
    }
}
