package me.moiclathy.edb.core.datafile.analysis

import me.moiclathy.edb.core.datafile.format.DataFileFormatRegistry
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.LocalDataFileStorage
import me.moiclathy.edb.core.datafile.upload.DefaultDataFileNameSanitizer
import me.moiclathy.edb.core.datafile.upload.DefaultDataFileStorageKeyGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class AnalysisArtifactServiceTest {
    @Test
    fun `stores analysis output with task lineage and reserved metadata`(@TempDir root: Path) {
        val storage = LocalDataFileStorage(root)
        val sourceStored = storage.store(
            "projects/7/data/video.mp4",
            ByteArrayInputStream("video".toByteArray())
        )
        val repository = InMemoryRepository(source(sourceStored.reference))
        val service = AnalysisArtifactService(
            repository = repository,
            storage = storage,
            formatRegistry = DataFileFormatRegistry(),
            fileNameSanitizer = DefaultDataFileNameSanitizer(),
            storageKeyGenerator = DefaultDataFileStorageKeyGenerator { "analysis-1" },
            clock = Clock.fixed(Instant.parse("2026-08-10T15:00:00Z"), ZoneOffset.UTC)
        )

        val result = service.create(
            CreateAnalysisArtifactCommand(
                projectId = 7,
                sourceDataFileIds = setOf(1),
                requestedBy = 11,
                producingTaskId = 81,
                analysisType = "pose-estimation",
                analysisTool = "DeepLabCut",
                outputFormat = DataFileFormat.HDF5,
                outputFileName = "pose-results.h5",
                content = ByteArrayInputStream("analysis-output".toByteArray()),
                metadata = DataFileMetadata(
                    mapOf("analysisType" to DataFileMetadataValue.StringValue("spoofed"))
                )
            )
        )

        assertEquals(DataFileKind.ANALYSIS_RESULT, result.kind)
        assertEquals(DataFileOrigin.ANALYSIS, result.provenance.origin)
        assertEquals(setOf(1), result.provenance.parentDataFileIds)
        assertEquals(81, result.provenance.producingTaskId)
        assertEquals("DeepLabCut", result.provenance.externalSystem)
        assertEquals("application/x-hdf5", result.mediaType)
        assertEquals(
            DataFileMetadataValue.StringValue("pose-estimation"),
            result.metadata.values["analysisType"]
        )
        assertEquals(
            "analysis-output",
            storage.open(requireNotNull(result.storage).key).bufferedReader().use { it.readText() }
        )
    }

    private fun source(reference: me.moiclathy.edb.core.datafile.model.DataFileStorageReference) = DataFile(
        id = 1,
        projectId = 7,
        createdByUserId = 11,
        kind = DataFileKind.RAW_DATA,
        format = DataFileFormat.VIDEO,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "video.mp4",
        storage = reference,
        provenance = DataFileProvenance(DataFileOrigin.UPLOAD),
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    )

    private class InMemoryRepository(source: DataFile) : DataFileRepository {
        private val files = linkedMapOf(requireNotNull(source.id) to source)
        override fun save(dataFile: DataFile): DataFile = dataFile.copy(id = 2).also { files[2] = it }
        override fun findById(id: Int): DataFile? = files[id]
        override fun findByProjectId(projectId: Int): List<DataFile> = files.values.filter { it.projectId == projectId }
        override fun update(dataFile: DataFile): DataFile = dataFile.also { files[requireNotNull(it.id)] = it }
    }
}
