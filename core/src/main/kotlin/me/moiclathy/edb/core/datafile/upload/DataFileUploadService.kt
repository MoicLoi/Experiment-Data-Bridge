package me.moiclathy.edb.core.datafile.upload

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.DataFileStorage
import me.moiclathy.edb.core.datafile.storage.DataFileStorageException
import me.moiclathy.edb.core.datafile.storage.compensateDelete
import java.io.IOException
import java.io.InputStream
import java.time.Clock

data class DataFileUploadPolicy(
    val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES
) {
    init {
        require(maxSizeBytes > 0) { "Maximum DataFile upload size must be positive." }
    }

    companion object {
        const val DEFAULT_MAX_SIZE_BYTES: Long = 1024L * 1024L * 1024L
    }
}

data class DataFileUploadRequest(
    val projectId: Int,
    val uploadedByUserId: Int,
    val originalFileName: String,
    val input: InputStream,
    val declaredMediaType: String? = null,
    val metadata: DataFileMetadata = DataFileMetadata.EMPTY
) {
    init {
        require(projectId > 0) { "Project id must be positive." }
        require(uploadedByUserId > 0) { "Uploader user id must be positive." }
        require(originalFileName.isNotBlank()) { "Uploaded file name must not be blank." }
    }
}

open class DataFileUploadException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class DataFileUploadTooLargeException(val maxSizeBytes: Long) :
    DataFileUploadException("DataFile upload exceeds the maximum size of $maxSizeBytes bytes.")

class DataFileUploadPersistenceException(cause: Throwable) :
    DataFileUploadException("Uploaded content was committed but its DataFile record could not be persisted.", cause)

class DataFileUploadService(
    private val storage: DataFileStorage,
    private val repository: DataFileRepository,
    private val policy: DataFileUploadPolicy = DataFileUploadPolicy(),
    private val fileNameSanitizer: DataFileNameSanitizer = DefaultDataFileNameSanitizer(),
    private val formatDetector: DataFileFormatDetector = DefaultDataFileFormatDetector(),
    private val storageKeyGenerator: DataFileStorageKeyGenerator = DefaultDataFileStorageKeyGenerator(),
    private val clock: Clock = Clock.systemUTC()
) {
    fun upload(request: DataFileUploadRequest): DataFile {
        val sanitizedFileName = fileNameSanitizer.sanitize(request.originalFileName)
        val detected = formatDetector.detect(sanitizedFileName, request.declaredMediaType)
        val storageKey = storageKeyGenerator.generate(request.projectId, sanitizedFileName)
        val stored = try {
            storage.store(storageKey, SizeLimitedInputStream(request.input, policy.maxSizeBytes))
        } catch (failure: DataFileStorageException) {
            if (failure.causedByUploadLimit()) {
                throw DataFileUploadTooLargeException(policy.maxSizeBytes)
            }
            throw DataFileUploadException("Failed to store uploaded DataFile.", failure)
        }
        val now = clock.instant()
        val dataFile = DataFile(
            projectId = request.projectId,
            createdByUserId = request.uploadedByUserId,
            kind = DataFileKind.RAW_DATA,
            format = detected.format,
            status = DataFileStatus.AVAILABLE,
            originalFileName = sanitizedFileName,
            mediaType = detected.mediaType,
            sizeBytes = stored.sizeBytes,
            sha256 = stored.sha256,
            storage = stored.reference,
            provenance = DataFileProvenance(origin = DataFileOrigin.UPLOAD),
            metadata = request.metadata,
            createdAt = now,
            updatedAt = now
        )
        return try {
            repository.save(dataFile)
        } catch (failure: Exception) {
            val persistenceFailure = DataFileUploadPersistenceException(failure)
            storage.compensateDelete(stored.reference.key, persistenceFailure)
            throw persistenceFailure
        }
    }
}

private class UploadLimitExceededIOException : IOException()

private class SizeLimitedInputStream(
    private val delegate: InputStream,
    private val limit: Long
) : InputStream() {
    private var consumed = 0L

    override fun read(): Int {
        val value = delegate.read()
        if (value >= 0) record(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = delegate.read(buffer, offset, length)
        if (read > 0) record(read.toLong())
        return read
    }

    private fun record(count: Long) {
        consumed += count
        if (consumed > limit) throw UploadLimitExceededIOException()
    }
}

private fun Throwable.causedByUploadLimit(): Boolean =
    generateSequence(this as Throwable?) { it.cause }
        .any { it is UploadLimitExceededIOException }
