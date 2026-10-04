package me.moiclathy.edb.core.datafile.report

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.datafile.format.DataFileFormatRegistry
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class ReportArtifactServiceTest {
    @Test
    fun `generator output is stored and persisted as a report artifact`(@TempDir root: Path) = runBlocking {
        val storage = LocalDataFileStorage(root)
        val storedSource = storage.store("projects/7/data/source.csv", ByteArrayInputStream("x,y\n1,2".toByteArray()))
        val repository = InMemoryRepository(source(storedSource.reference))
        val generator = object : ReportGenerator {
            override val id = "summary"
            override val supportedFormats = setOf(DataFileFormat.PDF)

            override suspend fun generate(input: ReportGenerationInput): ReportGeneratorOutput {
                assertEquals("Mouse summary", input.parameters["title"])
                assertEquals("x,y\n1,2", input.sources.single().content.bufferedReader().readText())
                return ReportGeneratorOutput(ByteArrayInputStream("PDF report".toByteArray()))
            }
        }
        val service = ReportArtifactService(
            registry = ReportGeneratorRegistry(listOf(generator)),
            repository = repository,
            storage = storage,
            formatRegistry = DataFileFormatRegistry(),
            fileNameSanitizer = DefaultDataFileNameSanitizer(),
            storageKeyGenerator = DefaultDataFileStorageKeyGenerator { "report-1" },
            clock = Clock.fixed(Instant.parse("2026-08-10T14:00:00Z"), ZoneOffset.UTC)
        )

        val report = service.generate(
            GenerateReportArtifactCommand(
                projectId = 7,
                definition = ReportDefinition(
                    id = "mouse-summary",
                    title = "Mouse summary",
                    generatorId = "summary",
                    outputFormat = DataFileFormat.PDF,
                    outputFileName = "summary.pdf",
                    defaultParameters = mapOf("title" to "Default")
                ),
                sourceDataFileIds = setOf(1),
                requestedBy = 11,
                producingTaskId = 81,
                parameters = mapOf("title" to "Mouse summary")
            )
        )

        assertEquals(DataFileKind.REPORT, report.kind)
        assertEquals(DataFileOrigin.REPORT_GENERATION, report.provenance.origin)
        assertEquals(setOf(1), report.provenance.parentDataFileIds)
        assertEquals(81, report.provenance.producingTaskId)
        assertEquals("application/pdf", report.mediaType)
        assertEquals(
            "PDF report",
            storage.open(requireNotNull(report.storage).key).bufferedReader().use { it.readText() }
        )
    }

    private fun source(reference: me.moiclathy.edb.core.datafile.model.DataFileStorageReference) = DataFile(
        id = 1,
        projectId = 7,
        createdByUserId = 11,
        kind = DataFileKind.RAW_DATA,
        format = DataFileFormat.CSV,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "source.csv",
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
