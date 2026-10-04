package me.moiclathy.edb.core.datafile.serialization

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.model.DataFileStorageReference
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonDataFileDescriptorCodecTest {
    private val codec = JsonDataFileDescriptorCodec()

    @Test
    fun `supports independent serializer and deserializer contracts`() {
        val serializer: DataFileDescriptorSerializer = codec
        val deserializer: DataFileDescriptorDeserializer = codec

        val restored = deserializer.deserializeDataFile(serializer.serialize(minimalDataFile()))

        assertEquals(minimalDataFile(), restored)
    }

    @Test
    fun `round trips a versioned descriptor with lineage storage and structured metadata`() {
        val file = DataFile(
            id = 42,
            projectId = 7,
            createdByUserId = 3,
            kind = DataFileKind.DERIVED_DATA,
            format = DataFileFormat.HDF5,
            status = DataFileStatus.AVAILABLE,
            originalFileName = "pose-results.h5",
            mediaType = "application/x-hdf5",
            sizeBytes = 158_240,
            sha256 = "a".repeat(64),
            storage = DataFileStorageReference("local", "projects/7/data/42/pose-results.h5"),
            provenance = DataFileProvenance(
                origin = DataFileOrigin.CONVERSION,
                parentDataFileIds = setOf(10, 11),
                producingTaskId = 81,
                externalSystem = "DeepLabCut"
            ),
            metadata = DataFileMetadata(
                mapOf(
                    "confidenceThreshold" to DataFileMetadataValue.NumberValue(BigDecimal("0.9500")),
                    "validated" to DataFileMetadataValue.BooleanValue(true),
                    "labels" to DataFileMetadataValue.ArrayValue(
                        listOf(
                            DataFileMetadataValue.StringValue("nose"),
                            DataFileMetadataValue.StringValue("tail")
                        )
                    ),
                    "subject" to DataFileMetadataValue.ObjectValue(
                        mapOf("id" to DataFileMetadataValue.StringValue("mouse-01"))
                    )
                )
            ),
            createdAt = Instant.parse("2026-08-10T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-10T10:05:00Z")
        )

        val json = codec.serialize(file)
        val restored = codec.deserializeDataFile(json)

        assertEquals(file, restored)
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("0.95"))
    }

    @Test
    fun `strictly rejects unknown properties unsupported versions and invalid domain values`() {
        val valid = codec.serialize(minimalDataFile())

        assertFailsWith<DataFileDescriptorJsonException> {
            codec.deserialize(valid.replaceFirst("{", "{\"unexpected\":true,"))
        }
        assertFailsWith<DataFileDescriptorJsonException> {
            codec.deserialize(valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
        }
        assertFailsWith<DataFileDescriptorJsonException> {
            codec.deserialize(valid.replace("\"projectId\": 1", "\"projectId\": 0"))
        }
    }

    private fun minimalDataFile() = DataFile(
        projectId = 1,
        createdByUserId = 2,
        kind = DataFileKind.RAW_DATA,
        format = DataFileFormat.CSV,
        status = DataFileStatus.REGISTERED,
        originalFileName = "raw.csv",
        provenance = DataFileProvenance(DataFileOrigin.UPLOAD),
        createdAt = Instant.parse("2026-08-10T10:00:00Z")
    )
}
