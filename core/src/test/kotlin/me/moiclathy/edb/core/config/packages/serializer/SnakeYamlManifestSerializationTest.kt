package me.moiclathy.edb.core.config.packages.serializer

import me.moiclathy.edb.core.config.packages.EdbManifest
import me.moiclathy.edb.core.config.packages.EdbManifestMetadata
import me.moiclathy.edb.core.config.packages.PackageVersion
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class SnakeYamlManifestSerializationTest {

    private val serializer = SnakeYamlManifestSerializer()
    private val deserializer = SnakeYamlManifestDeserializer()

    @Test
    fun `metadata survives manifest round trip`() {
        val manifest = EdbManifest(
            packageVersion = PackageVersion.CURRENT,
            edbVersion = "0.24",
            projectName = "research-project",
            projectId = 42,
            exportedAt = Instant.parse("2026-08-09T10:15:30Z"),
            author = "researcher",
            metadata = EdbManifestMetadata(
                description = "RNA sequencing analysis",
                category = "bioinformatics",
                tags = listOf("rna", "sequencing"),
                updatedAt = Instant.parse("2026-08-08T09:00:00Z"),
                dataSource = "laboratory-a"
            )
        )

        val restored = deserializer.deserialize(serializer.serialize(manifest))

        assertEquals(manifest, restored)
    }

    @Test
    fun `legacy manifest without metadata remains readable`() {
        val legacyYaml = """
            packageVersion: 1
            edbVersion: '0.22'
            projectName: legacy-project
            projectId: 7
            author: null
            exportedAt: '2026-01-01T00:00:00Z'
        """.trimIndent()

        val restored = deserializer.deserialize(legacyYaml)

        assertEquals("legacy-project", restored.projectName)
        assertNull(restored.metadata)
    }
}
