package me.moiclathy.edb.core.datafile.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.model.DataFileStorageReference
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.DataFileStorage
import me.moiclathy.edb.core.datafile.upload.DataFileUploadService
import me.moiclathy.edb.core.database.model.Project
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.project.ProjectMode
import me.moiclathy.edb.core.security.PermissionChecker
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataFileApplicationServiceTest {
    @Test
    fun `enforces project scope before listing details and download`() {
        val uploadService = mockk<DataFileUploadService>()
        val repository = mockk<DataFileRepository>()
        val storage = mockk<DataFileStorage>()
        val projects = mockk<ProjectRepository>()
        val permissions = mockk<PermissionChecker>()
        val project = Project(7, "Lab", null, 11, ProjectMode.PERSONAL, null)
        val file = dataFile(projectId = 7)
        every { projects.findById(7) } returns project
        every { permissions.requireReadable(11, project) } returns true
        every { repository.findByProjectId(7) } returns listOf(file)
        every { repository.findById(42) } returns file
        every { storage.open("projects/7/data/file.csv") } returns ByteArrayInputStream("data".toByteArray())
        val service = DataFileApplicationService(uploadService, repository, storage, projects, permissions)

        assertEquals(listOf(file), service.list(7, 11))
        assertEquals(file, service.get(7, 42, 11))
        assertEquals("data", service.openDownload(7, 42, 11).input.bufferedReader().use { it.readText() })

        every { repository.findById(42) } returns dataFile(projectId = 8)
        assertFailsWith<DataFileNotFoundException> { service.get(7, 42, 11) }
        verify(exactly = 1) { storage.open(any()) }
    }

    private fun dataFile(projectId: Int) = DataFile(
        id = 42,
        projectId = projectId,
        createdByUserId = 11,
        kind = DataFileKind.RAW_DATA,
        format = DataFileFormat.CSV,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "file.csv",
        mediaType = "text/csv",
        sizeBytes = 4,
        sha256 = "c".repeat(64),
        storage = DataFileStorageReference("local", "projects/7/data/file.csv"),
        provenance = DataFileProvenance(DataFileOrigin.UPLOAD),
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    )
}

