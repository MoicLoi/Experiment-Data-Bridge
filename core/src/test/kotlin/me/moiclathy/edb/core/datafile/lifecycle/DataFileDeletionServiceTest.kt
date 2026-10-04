package me.moiclathy.edb.core.datafile.lifecycle

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.DataFileStorage
import me.moiclathy.edb.core.datafile.storage.LocalDataFileStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class DataFileDeletionServiceTest {
    @Test
    fun `marks deleted removes content and permits cleanup retry`(@TempDir root: Path) {
        val storage = LocalDataFileStorage(root)
        val key = "projects/7/data/upload-1/lab.csv"
        val stored = storage.store(key, ByteArrayInputStream("a,b\n1,2".toByteArray()))
        val repository = InMemoryRepository(
            DataFile(
                id = 42,
                projectId = 7,
                createdByUserId = 3,
                kind = DataFileKind.RAW_DATA,
                format = DataFileFormat.CSV,
                status = DataFileStatus.AVAILABLE,
                originalFileName = "lab.csv",
                mediaType = "text/csv",
                sizeBytes = stored.sizeBytes,
                sha256 = stored.sha256,
                storage = stored.reference,
                provenance = DataFileProvenance(DataFileOrigin.UPLOAD),
                createdAt = Instant.parse("2026-08-10T12:00:00Z")
            )
        )
        val service = DataFileDeletionService(
            repository,
            storage,
            Clock.fixed(Instant.parse("2026-08-10T13:00:00Z"), ZoneOffset.UTC)
        )

        val deleted = service.delete(7, 42)
        val retried = service.delete(7, 42)

        assertEquals(DataFileStatus.DELETED, deleted.status)
        assertEquals(Instant.parse("2026-08-10T13:00:00Z"), deleted.updatedAt)
        assertEquals(deleted, retried)
        assertFalse(storage.exists(key))
        assertEquals(1, repository.updateCount)
    }

    @Test
    fun `retains deleted status when physical cleanup fails and retries it`(@TempDir root: Path) {
        val localStorage = LocalDataFileStorage(root)
        val key = "projects/7/data/upload-2/lab.csv"
        val stored = localStorage.store(key, ByteArrayInputStream("content".toByteArray()))
        val repository = InMemoryRepository(dataFile(stored.reference, stored.sizeBytes, stored.sha256))
        var failCleanup = true
        val storage = object : DataFileStorage by localStorage {
            override fun delete(storageKey: String): Boolean {
                if (failCleanup) {
                    failCleanup = false
                    error("file is temporarily locked")
                }
                return localStorage.delete(storageKey)
            }
        }
        val service = DataFileDeletionService(repository, storage)

        assertFailsWith<DataFileContentCleanupException> { service.delete(7, 42) }
        assertEquals(DataFileStatus.DELETED, repository.findById(42)?.status)
        assertEquals(1, repository.updateCount)

        service.delete(7, 42)

        assertFalse(localStorage.exists(key))
        assertEquals(1, repository.updateCount)
    }

    private fun dataFile(
        storage: me.moiclathy.edb.core.datafile.model.DataFileStorageReference,
        sizeBytes: Long,
        sha256: String
    ) = DataFile(
        id = 42,
        projectId = 7,
        createdByUserId = 3,
        kind = DataFileKind.RAW_DATA,
        format = DataFileFormat.CSV,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "lab.csv",
        mediaType = "text/csv",
        sizeBytes = sizeBytes,
        sha256 = sha256,
        storage = storage,
        provenance = DataFileProvenance(DataFileOrigin.UPLOAD),
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    )

    private class InMemoryRepository(initial: DataFile) : DataFileRepository {
        private var value = initial
        var updateCount = 0
            private set

        override fun save(dataFile: DataFile): DataFile = dataFile
        override fun findById(id: Int): DataFile? = value.takeIf { it.id == id }
        override fun findByProjectId(projectId: Int): List<DataFile> =
            listOf(value).filter { it.projectId == projectId }

        override fun update(dataFile: DataFile): DataFile {
            updateCount++
            value = dataFile
            return dataFile
        }
    }
}
