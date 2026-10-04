package me.moiclathy.edb.core.datafile.lifecycle

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.DataFileStorage
import me.moiclathy.edb.core.exception.EDBException
import java.time.Clock

class DataFileLifecycleNotFoundException(id: Int) : EDBException(
    code = "DATA_FILE_NOT_FOUND",
    message = "DataFile $id not found"
)

class DataFileContentCleanupException(
    val dataFile: DataFile,
    cause: Throwable
) : EDBException(
    code = "DATA_FILE_CLEANUP_FAILED",
    message = "DataFile ${dataFile.id} was marked DELETED but its content cleanup failed: ${cause.message}"
) {
    init {
        initCause(cause)
    }
}

/** Soft-deletes the database record first, then idempotently removes its physical content. */
class DataFileDeletionService(
    private val repository: DataFileRepository,
    private val storage: DataFileStorage,
    private val clock: Clock = Clock.systemUTC()
) {
    fun delete(projectId: Int, dataFileId: Int): DataFile {
        require(projectId > 0 && dataFileId > 0) { "Project and DataFile ids must be positive." }
        val current = repository.findById(dataFileId)
            ?.takeIf { it.projectId == projectId }
            ?: throw DataFileLifecycleNotFoundException(dataFileId)
        val deleted = if (current.status == DataFileStatus.DELETED) {
            current
        } else {
            repository.update(
                current.copy(status = DataFileStatus.DELETED, updatedAt = clock.instant())
            )
        }
        val key = deleted.storage?.key ?: return deleted
        try {
            storage.delete(key)
        } catch (failure: Exception) {
            throw DataFileContentCleanupException(deleted, failure)
        }
        return deleted
    }
}
