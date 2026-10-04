package me.moiclathy.edb.core.config.metadata.service

import me.moiclathy.edb.core.config.metadata.model.ConfigurationMetadata
import me.moiclathy.edb.core.config.metadata.repository.ConfigurationMetadataRepository
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ConfigurationMetadataServiceImportTest {

    @Test
    fun `import creates metadata and preserves source timestamp`() {
        val repository = InMemoryConfigurationMetadataRepository()
        val service = ConfigurationMetadataService(repository)
        val updatedAt = Instant.parse("2026-08-08T09:00:00Z")

        val imported = service.importMetadata(
            ConfigurationMetadata(
                projectId = 200,
                description = "Imported description",
                category = "research",
                tags = listOf(" genomics ", "genomics", ""),
                updatedAt = updatedAt,
                dataSource = "archive"
            )
        )

        assertEquals(200, imported.projectId)
        assertEquals(listOf("genomics"), imported.tags)
        assertEquals(updatedAt, imported.updatedAt)
        assertEquals(imported, repository.findByProjectId(200))
    }

    @Test
    fun `import replaces metadata already stored for target project`() {
        val repository = InMemoryConfigurationMetadataRepository()
        val service = ConfigurationMetadataService(repository)
        repository.save(
            ConfigurationMetadata(
                projectId = 300,
                description = "Old",
                category = null,
                tags = emptyList(),
                updatedAt = Instant.EPOCH,
                dataSource = null
            )
        )
        val importedAt = Instant.parse("2026-08-09T12:00:00Z")

        val imported = service.importMetadata(
            ConfigurationMetadata(
                projectId = 300,
                description = "New",
                category = "physics",
                tags = listOf("simulation"),
                updatedAt = importedAt,
                dataSource = "cluster"
            )
        )

        assertEquals("New", imported.description)
        assertEquals(importedAt, imported.updatedAt)
        assertEquals(imported, repository.findByProjectId(300))
    }

    private class InMemoryConfigurationMetadataRepository : ConfigurationMetadataRepository {
        private val values = mutableMapOf<Int, ConfigurationMetadata>()

        override fun save(metadata: ConfigurationMetadata): ConfigurationMetadata {
            check(metadata.projectId !in values)
            values[metadata.projectId] = metadata
            return metadata
        }

        override fun findByProjectId(projectId: Int): ConfigurationMetadata? = values[projectId]

        override fun update(metadata: ConfigurationMetadata): ConfigurationMetadata {
            check(metadata.projectId in values)
            values[metadata.projectId] = metadata
            return metadata
        }

        override fun delete(projectId: Int) {
            values.remove(projectId)
        }

        override fun exists(projectId: Int): Boolean = projectId in values

        override fun findAll(): List<ConfigurationMetadata> = values.values.toList()
    }
}
