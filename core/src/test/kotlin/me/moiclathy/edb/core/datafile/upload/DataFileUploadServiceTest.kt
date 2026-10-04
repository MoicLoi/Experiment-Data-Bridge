package me.moiclathy.edb.core.datafile.upload

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.LocalDataFileStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataFileUploadServiceTest {
    @Test
    fun `sanitizes detects stores and returns an available DataFile`(@TempDir root: Path) {
        val storage = LocalDataFileStorage(root)
        val service = service(storage, maxSize = 100)

        val file = service.upload(
            DataFileUploadRequest(
                projectId = 7,
                uploadedByUserId = 3,
                originalFileName = "C:\\fakepath\\测 试?.CSV",
                input = ByteArrayInputStream("a,b\n1,2".toByteArray()),
                declaredMediaType = "application/octet-stream"
            )
        )

        assertEquals("测_试_.CSV", file.originalFileName)
        assertEquals(DataFileFormat.CSV, file.format)
        assertEquals("text/csv", file.mediaType)
        assertEquals(DataFileStatus.AVAILABLE, file.status)
        assertEquals(99, file.id)
        assertEquals(3, file.createdByUserId)
        assertEquals(Instant.parse("2026-08-10T12:00:00Z"), file.createdAt)
        assertEquals("projects/7/data/upload-1/测_试_.CSV", file.storage?.key)
        assertEquals("a,b\n1,2", storage.open(requireNotNull(file.storage).key).bufferedReader().use { it.readText() })
        assertTrue(file.sha256?.matches(Regex("^[0-9a-f]{64}$")) == true)
    }

    @Test
    fun `enforces streaming size limit and cleans staging content`(@TempDir root: Path) {
        val storage = LocalDataFileStorage(root)
        val service = service(storage, maxSize = 5)

        assertFailsWith<DataFileUploadTooLargeException> {
            service.upload(
                DataFileUploadRequest(
                    projectId = 1,
                    uploadedByUserId = 2,
                    originalFileName = "values.json",
                    input = ByteArrayInputStream("123456".toByteArray()),
                    declaredMediaType = "application/json"
                )
            )
        }

        assertEquals(
            0,
            Files.list(root.resolve(LocalDataFileStorage.STAGING_DIRECTORY)).use { it.count() }
        )
        assertEquals(1, DefaultDataFileNameSanitizer().sanitize("CON.csv").count { it == '_' })
        assertEquals(
            DataFileFormat.JSON,
            DefaultDataFileFormatDetector().detect("extensionless", "application/json; charset=utf-8").format
        )
    }

    @Test
    fun `removes committed content when persistence fails`(@TempDir root: Path) {
        val storage = LocalDataFileStorage(root)
        val service = DataFileUploadService(
            storage = storage,
            repository = object : DataFileRepository {
                override fun save(dataFile: DataFile): DataFile = error("database unavailable")
                override fun findById(id: Int): DataFile? = null
                override fun findByProjectId(projectId: Int): List<DataFile> = emptyList()
                override fun update(dataFile: DataFile): DataFile = dataFile
            },
            storageKeyGenerator = DefaultDataFileStorageKeyGenerator { "failed-upload" }
        )

        assertFailsWith<DataFileUploadPersistenceException> {
            service.upload(
                DataFileUploadRequest(
                    projectId = 7,
                    uploadedByUserId = 3,
                    originalFileName = "lab.csv",
                    input = ByteArrayInputStream("a,b\n1,2".toByteArray())
                )
            )
        }

        assertEquals(false, storage.exists("projects/7/data/failed-upload/lab.csv"))
    }

    private fun service(storage: LocalDataFileStorage, maxSize: Long) = DataFileUploadService(
        storage = storage,
        repository = RecordingDataFileRepository(),
        policy = DataFileUploadPolicy(maxSize),
        storageKeyGenerator = DefaultDataFileStorageKeyGenerator { "upload-1" },
        clock = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC)
    )

    private class RecordingDataFileRepository : DataFileRepository {
        private var saved: DataFile? = null

        override fun save(dataFile: DataFile): DataFile = dataFile.copy(id = 99).also { saved = it }
        override fun findById(id: Int): DataFile? = saved?.takeIf { it.id == id }
        override fun findByProjectId(projectId: Int): List<DataFile> =
            listOfNotNull(saved?.takeIf { it.projectId == projectId })
        override fun update(dataFile: DataFile): DataFile = dataFile.also { saved = it }
    }
}
