package me.moiclathy.edb.core.datafile.storage

import me.moiclathy.edb.core.datafile.model.DataFileStorageReference
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Local immutable file storage with same-filesystem staging and atomic commit. */
class LocalDataFileStorage(
    rootDirectory: Path,
    override val provider: String = DEFAULT_PROVIDER
) : DataFileStorage, DataFileStorageInventory {
    private val root = rootDirectory.toAbsolutePath().normalize()
    private val staging = root.resolve(STAGING_DIRECTORY)
    private val owner = Any()

    init {
        require(provider.isNotBlank()) { "DataFile storage provider must not be blank." }
        prepareStorageDirectory(root)
        prepareStorageDirectory(staging)
    }

    override fun stage(input: InputStream): StagedDataFile {
        val temporary = try {
            Files.createTempFile(staging, "datafile-", ".tmp")
        } catch (failure: Exception) {
            throw DataFileStorageException("Failed to create a DataFile staging file.", failure)
        }

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            Files.newOutputStream(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    size += read
                }
            }
            StagedDataFile(owner, temporary, size, digest.digest().toHex())
        } catch (failure: Exception) {
            runCatching { Files.deleteIfExists(temporary) }
                .exceptionOrNull()
                ?.let(failure::addSuppressed)
            throw DataFileStorageException("Failed to stage DataFile content.", failure)
        }
    }

    override fun commit(staged: StagedDataFile, storageKey: String): StoredDataFile {
        val target = resolveStorageKey(storageKey)
        prepareTargetParent(target.parent)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw DataFileAlreadyExistsException(storageKey)
        }

        return staged.commit(owner) { temporary ->
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (failure: AtomicMoveNotSupportedException) {
                throw AtomicDataFileCommitNotSupportedException(failure)
            } catch (failure: FileAlreadyExistsException) {
                throw DataFileAlreadyExistsException(storageKey)
            } catch (failure: Exception) {
                throw DataFileStorageException(
                    "Failed to atomically commit DataFile to storage key: $storageKey",
                    failure
                )
            }
            StoredDataFile(
                reference = DataFileStorageReference(provider, storageKey),
                sizeBytes = staged.sizeBytes,
                sha256 = staged.sha256
            )
        }
    }

    override fun open(storageKey: String): InputStream {
        val target = resolveStorageKey(storageKey)
        ensurePathContainsNoSymbolicLinks(target)
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw DataFileNotFoundException(storageKey)
        }
        return try {
            Files.newInputStream(target, StandardOpenOption.READ)
        } catch (failure: Exception) {
            throw DataFileStorageException("Failed to open DataFile storage key: $storageKey", failure)
        }
    }

    override fun exists(storageKey: String): Boolean {
        val target = resolveStorageKey(storageKey)
        ensurePathContainsNoSymbolicLinks(target)
        return Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
    }

    override fun delete(storageKey: String): Boolean {
        val target = resolveStorageKey(storageKey)
        ensurePathContainsNoSymbolicLinks(target)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw DataFileStorageException("DataFile storage key is not a regular file: $storageKey")
        }
        return try {
            val deleted = Files.deleteIfExists(target)
            if (deleted) pruneEmptyParents(target.parent)
            deleted
        } catch (failure: DataFileStorageException) {
            throw failure
        } catch (failure: Exception) {
            throw DataFileStorageException("Failed to delete DataFile storage key: $storageKey", failure)
        }
    }

    override fun listCommitted(): List<DataFileStorageEntry> = try {
        Files.walk(root).use { paths ->
            paths.filter { path ->
                path != root &&
                    !path.startsWith(staging) &&
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            }.map { path ->
                DataFileStorageEntry(
                    key = root.relativize(path).joinToString("/") { it.toString() },
                    sizeBytes = Files.size(path),
                    lastModifiedAt = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant()
                )
            }.toList()
        }
    } catch (failure: Exception) {
        throw DataFileStorageException("Failed to inventory committed DataFile content.", failure)
    }

    private fun resolveStorageKey(storageKey: String): Path {
        if (storageKey.isBlank() || storageKey != storageKey.trim()) {
            throw InvalidDataFileStorageKeyException(storageKey, "key must not be blank or padded")
        }
        if ('\\' in storageKey || storageKey.startsWith('/') || storageKey.endsWith('/')) {
            throw InvalidDataFileStorageKeyException(storageKey, "use a relative forward-slash path")
        }
        val segments = storageKey.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) {
            throw InvalidDataFileStorageKeyException(storageKey, "empty, '.' and '..' segments are forbidden")
        }
        if (segments.first() == STAGING_DIRECTORY) {
            throw InvalidDataFileStorageKeyException(storageKey, "the staging namespace is reserved")
        }
        val target = segments.fold(root) { current, segment -> current.resolve(segment) }.normalize()
        if (!target.startsWith(root)) {
            throw InvalidDataFileStorageKeyException(storageKey, "key escapes the storage root")
        }
        return target
    }

    private fun prepareTargetParent(parent: Path) {
        val relative = root.relativize(parent)
        var current = root
        relative.forEach { segment ->
            current = current.resolve(segment)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw InvalidDataFileStorageKeyException(
                        root.relativize(parent).toString(),
                        "target path contains a symbolic link or non-directory segment"
                    )
                }
            } else {
                try {
                    Files.createDirectory(current)
                } catch (failure: Exception) {
                    throw DataFileStorageException("Failed to create DataFile storage directory: $current", failure)
                }
            }
        }
    }

    private fun ensurePathContainsNoSymbolicLinks(target: Path) {
        var current = root
        root.relativize(target).forEach { segment ->
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) {
                throw InvalidDataFileStorageKeyException(
                    root.relativize(target).toString(),
                    "path contains a symbolic link"
                )
            }
        }
    }

    private fun pruneEmptyParents(start: Path) {
        var current = start
        while (current != root && current != staging && current.startsWith(root)) {
            if (Files.isSymbolicLink(current)) return
            try {
                Files.delete(current)
            } catch (_: DirectoryNotEmptyException) {
                return
            }
            current = current.parent ?: return
        }
    }

    private fun prepareStorageDirectory(directory: Path) {
        try {
            Files.createDirectories(directory)
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw DataFileStorageException("DataFile storage directory is not a real directory: $directory")
            }
        } catch (failure: DataFileStorageException) {
            throw failure
        } catch (failure: Exception) {
            throw DataFileStorageException("Failed to initialize DataFile storage directory: $directory", failure)
        }
    }

    companion object {
        const val DEFAULT_PROVIDER = "local"
        const val STAGING_DIRECTORY = ".staging"
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
