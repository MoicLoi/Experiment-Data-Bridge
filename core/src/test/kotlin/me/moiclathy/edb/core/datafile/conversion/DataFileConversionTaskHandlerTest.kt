package me.moiclathy.edb.core.datafile.conversion

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.LocalDataFileStorage
import me.moiclathy.edb.core.datafile.upload.DefaultDataFileNameSanitizer
import me.moiclathy.edb.core.datafile.upload.DefaultDataFileStorageKeyGenerator
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.progress.TaskProgress
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataFileConversionTaskHandlerTest {
    @Test
    fun `registered conversion executes as task and persists a derived DataFile`(
        @TempDir root: Path
    ) = runBlocking {
        val storage = LocalDataFileStorage(root)
        val sourceStored = storage.store(
            "projects/7/data/source.csv",
            ByteArrayInputStream("x,y\n1,2".toByteArray())
        )
        val repository = InMemoryDataFileRepository(source(sourceStored.reference))
        val converter = object : DataFileConverter {
            override val sourceFormat = DataFileFormat.CSV
            override val targetFormat = DataFileFormat.JSON

            override suspend fun convert(input: DataFileConverterInput) = DataFileConverterOutput(
                fileName = "converted.json",
                content = input.content,
                mediaType = "application/json"
            )
        }
        val registry = DataFileConversionRegistry(listOf(converter))
        val service = DataFileConversionService(
            registry = registry,
            repository = repository,
            storage = storage,
            fileNameSanitizer = DefaultDataFileNameSanitizer(),
            storageKeyGenerator = DefaultDataFileStorageKeyGenerator { "conversion-1" },
            clock = Clock.fixed(Instant.parse("2026-08-10T13:00:00Z"), ZoneOffset.UTC)
        )
        val handler = DataFileConversionTaskHandler(service)
        val progress = mutableListOf<Int>()
        val task = Task.pending(
            projectId = 7,
            taskType = DataFileConversionTaskHandler.TASK_TYPE,
            configSnapshot = DataFileConversionTaskPayloadCodec.encode(
                DataFileConversionTaskPayload(1, DataFileFormat.JSON, mapOf("layout" to "records"))
            ),
            triggerUserId = 11,
            createdAt = Instant.parse("2026-08-10T12:59:00Z")
        ).copy(id = 81, status = TaskStatus.RUNNING)

        val result = handler.execute(task, context(progress))
        val derived = requireNotNull(repository.findById(2))

        assertEquals(listOf(10, 100), progress)
        assertEquals("2", result.metadata["dataFileId"])
        assertEquals("3", result.metadata["provenanceReportDataFileId"])
        assertEquals(DataFileKind.AUXILIARY, repository.findById(3)?.kind)
        assertEquals(DataFileFormat.JSON, derived.format)
        assertEquals(DataFileKind.DERIVED_DATA, derived.kind)
        assertEquals(setOf(1), derived.provenance.parentDataFileIds)
        assertEquals(81, derived.provenance.producingTaskId)
        assertEquals(
            "x,y\n1,2",
            storage.open(requireNotNull(derived.storage).key).bufferedReader().use { it.readText() }
        )
        assertTrue(DataFileConversionRegistry.SUPPORTED_SCIENTIFIC_FORMATS.containsAll(
            setOf(
                DataFileFormat.CSV,
                DataFileFormat.XLSX,
                DataFileFormat.JSON,
                DataFileFormat.HDF5,
                DataFileFormat.MAT
            )
        ))
    }

    private fun context(progress: MutableList<Int>) = object : TaskExecutionContext {
        override suspend fun ensureNotCancelled() = Unit

        override suspend fun reportProgress(percent: Int, message: String?): TaskProgress {
            progress += percent
            return TaskProgress(percent, message, Instant.parse("2026-08-10T13:00:00Z"))
        }
    }

    private fun source(reference: me.moiclathy.edb.core.datafile.model.DataFileStorageReference) =
        DataFile(
            id = 1,
            projectId = 7,
            createdByUserId = 11,
            kind = DataFileKind.RAW_DATA,
            format = DataFileFormat.CSV,
            status = DataFileStatus.AVAILABLE,
            originalFileName = "source.csv",
            mediaType = "text/csv",
            sizeBytes = 7,
            sha256 = "e".repeat(64),
            storage = reference,
            provenance = DataFileProvenance(DataFileOrigin.UPLOAD),
            createdAt = Instant.parse("2026-08-10T12:00:00Z")
        )

    private class InMemoryDataFileRepository(source: DataFile) : DataFileRepository {
        private val files = linkedMapOf(requireNotNull(source.id) to source)

        override fun save(dataFile: DataFile): DataFile {
            val id = (files.keys.maxOrNull() ?: 0) + 1
            return dataFile.copy(id = id).also { files[id] = it }
        }
        override fun findById(id: Int): DataFile? = files[id]
        override fun findByProjectId(projectId: Int): List<DataFile> =
            files.values.filter { it.projectId == projectId }
        override fun update(dataFile: DataFile): DataFile = dataFile.also { files[requireNotNull(it.id)] = it }
    }
}
