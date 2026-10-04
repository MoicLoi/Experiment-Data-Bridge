package me.moiclathy.edb.core.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import me.moiclathy.edb.core.config.metadata.model.ConfigurationMetadata
import me.moiclathy.edb.core.config.metadata.service.ConfigurationMetadataService
import me.moiclathy.edb.core.config.packages.EdbManifest
import me.moiclathy.edb.core.config.packages.EdbManifestMetadata
import me.moiclathy.edb.core.config.packages.EdbPackage
import me.moiclathy.edb.core.config.packages.EdbPackageImporter
import me.moiclathy.edb.core.config.packages.EdbPackageReader
import me.moiclathy.edb.core.config.packages.validation.PackageValidationResult
import me.moiclathy.edb.core.config.packages.validation.PackageValidationService
import me.moiclathy.edb.core.git.GitCommitInfo
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ProjectConfigurationPackageImportServiceTest {

    @Test
    fun `package metadata is rebound to target project`() {
        val sourceUpdatedAt = Instant.parse("2026-08-08T09:00:00Z")
        val pkg = EdbPackage(
            manifest = EdbManifest(
                packageVersion = 1,
                edbVersion = "0.24",
                projectName = "source",
                projectId = 10,
                exportedAt = Instant.parse("2026-08-09T09:00:00Z"),
                author = "researcher",
                metadata = EdbManifestMetadata(
                    description = "Portable metadata",
                    category = "research",
                    tags = listOf("dataset"),
                    updatedAt = sourceUpdatedAt,
                    dataSource = "source-system"
                )
            ),
            configuration = "version: 2"
        )
        val packageImporter = EdbPackageImporter(
            object : EdbPackageReader {
                override fun read(input: InputStream): EdbPackage = pkg
            }
        )
        val configurationService = mockk<ProjectConfigurationService>()
        val validationService = mockk<PackageValidationService>()
        val metadataService = mockk<ConfigurationMetadataService>()
        val commit = mockk<GitCommitInfo>()
        val capturedMetadata = slot<ConfigurationMetadata>()

        every { validationService.validate(pkg) } returns PackageValidationResult.success()
        every {
            configurationService.saveConfiguration(
                projectId = 99,
                userId = 5,
                yaml = pkg.configuration,
                author = "importer",
                messageBuilder = any()
            )
        } returns commit
        every { metadataService.importMetadata(capture(capturedMetadata)) } answers {
            capturedMetadata.captured
        }

        val service = ProjectConfigurationPackageImportService(
            packageImporter = packageImporter,
            configurationService = configurationService,
            validationService = validationService,
            metadataService = metadataService
        )

        val result = service.importPackage(
            projectId = 99,
            userId = 5,
            input = ByteArrayInputStream(byteArrayOf()),
            author = "importer"
        )

        assertEquals(commit, result)
        assertEquals(99, capturedMetadata.captured.projectId)
        assertEquals(sourceUpdatedAt, capturedMetadata.captured.updatedAt)
        verify(exactly = 1) { metadataService.importMetadata(any()) }
    }
}
