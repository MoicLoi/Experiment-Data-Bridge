package me.moiclathy.edb.core.datafile.storage

import me.moiclathy.edb.core.datafile.model.DataFileStorageReference
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class StoredDataFile(
    val reference: DataFileStorageReference,
    val sizeBytes: Long,
    val sha256: String
) {
    init {
        require(sizeBytes >= 0) { "Stored data file size must not be negative." }
        require(SHA_256.matches(sha256)) { "Stored data file SHA-256 is invalid." }
    }

    companion object {
        private val SHA_256 = Regex("^[0-9a-f]{64}$")
    }
}

class StagedDataFile internal constructor(
    internal val owner: Any,
    internal val path: Path,
    val sizeBytes: Long,
    val sha256: String
) : AutoCloseable {
    private val monitor = Any()
    private var active = true

    internal fun <T> commit(expectedOwner: Any, action: (Path) -> T): T = synchronized(monitor) {
        check(owner === expectedOwner) { "Staged data file belongs to another storage instance." }
        check(active) { "Staged data file is no longer active." }
        val result = action(path)
        active = false
        result
    }

    override fun close() = synchronized(monitor) {
        if (active) {
            try {
                Files.deleteIfExists(path)
                active = false
            } catch (failure: IOException) {
                throw DataFileStorageException("Failed to remove staged data file.", failure)
            }
        }
    }
}

interface DataFileStorage {
    fun stage(input: InputStream): StagedDataFile

    fun commit(staged: StagedDataFile, storageKey: String): StoredDataFile

    fun store(storageKey: String, input: InputStream): StoredDataFile =
        stage(input).use { staged -> commit(staged, storageKey) }

    fun open(storageKey: String): InputStream

    fun exists(storageKey: String): Boolean

    /** Idempotently removes committed content. Returns true only when content existed. */
    fun delete(storageKey: String): Boolean
}

data class DataFileStorageEntry(
    val key: String,
    val sizeBytes: Long,
    val lastModifiedAt: Instant
)

/** Optional inventory capability used for orphan reconciliation. */
interface DataFileStorageInventory {
    val provider: String
    fun listCommitted(): List<DataFileStorageEntry>
}

internal fun DataFileStorage.compensateDelete(storageKey: String, failure: Throwable) {
    try {
        delete(storageKey)
    } catch (cleanupFailure: Exception) {
        failure.addSuppressed(cleanupFailure)
    }
}

open class DataFileStorageException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

class InvalidDataFileStorageKeyException(key: String, reason: String) :
    DataFileStorageException("Invalid data file storage key '$key': $reason")

class DataFileAlreadyExistsException(key: String) :
    DataFileStorageException("Data file storage key already exists: $key")

class DataFileNotFoundException(key: String) :
    DataFileStorageException("Data file storage key was not found: $key")

class AtomicDataFileCommitNotSupportedException(cause: Throwable) :
    DataFileStorageException("The configured file system does not support atomic DataFile commits.", cause)
