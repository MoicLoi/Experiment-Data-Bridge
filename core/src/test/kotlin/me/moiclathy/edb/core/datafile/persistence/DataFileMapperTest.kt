package me.moiclathy.edb.core.datafile.persistence

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

class DataFileMapperTest {
    @Test
    fun `DataFile survives persistence mapping with lineage and structured metadata`() {
        val file = DataFile(
            id = 21,
            projectId = 7,
            createdByUserId = 3,
            kind = DataFileKind.DERIVED_DATA,
            format = DataFileFormat.MAT,
            status = DataFileStatus.AVAILABLE,
            originalFileName = "analysis.mat",
            mediaType = "application/x-matlab-data",
            sizeBytes = 4096,
            sha256 = "b".repeat(64),
            storage = DataFileStorageReference("local", "projects/7/data/analysis.mat"),
            provenance = DataFileProvenance(
                origin = DataFileOrigin.CONVERSION,
                parentDataFileIds = setOf(10, 11),
                producingTaskId = 81,
                externalSystem = "MATLAB"
            ),
            metadata = DataFileMetadata(
                mapOf(
                    "sampleRate" to DataFileMetadataValue.NumberValue(BigDecimal("120.0")),
                    "validated" to DataFileMetadataValue.BooleanValue(true)
                )
            ),
            createdAt = Instant.parse("2026-08-10T12:00:00Z"),
            updatedAt = Instant.parse("2026-08-10T12:01:00Z")
        )
        val mapper = DataFileMapper()

        assertEquals(file, mapper.toModel(mapper.toEntity(file)))
    }
}
