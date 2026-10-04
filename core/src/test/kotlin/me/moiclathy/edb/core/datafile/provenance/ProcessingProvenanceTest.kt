package me.moiclathy.edb.core.datafile.provenance

import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals

class ProcessingProvenanceTest {
    @Test
    fun `combines cleaning rule parameters with worker row counts`() {
        val metadata = DataFileMetadata(mapOf(
            "inputRows" to DataFileMetadataValue.NumberValue(BigDecimal(155)),
            "outputRows" to DataFileMetadataValue.NumberValue(BigDecimal(147)),
            "rules" to DataFileMetadataValue.ArrayValue(listOf(DataFileMetadataValue.ObjectValue(mapOf(
                "type" to DataFileMetadataValue.StringValue("dropRowsWithMissing"),
                "affectedRows" to DataFileMetadataValue.NumberValue(BigDecimal(8))
            ))))
        ))
        val provenance = ProcessingProvenanceFactory.analysis(
            "tabular-clean", mapOf("operation" to "tabular-clean", "spec" to """{"schemaVersion":1,"rules":[{"type":"dropRowsWithMissing","columns":["weight"]}]}"""),
            metadata, "python-http", "python-http", "1", setOf(9), 10, 11, 12,
            Instant.parse("2026-08-15T12:00:00Z"), Instant.parse("2026-08-15T12:00:01Z")
        )
        assertEquals(155, provenance.inputRows)
        assertEquals(147, provenance.outputRows)
        assertEquals(8, provenance.affectedRows)
        assertEquals("weight", provenance.steps.single()["columns"]?.toString()?.trim('[', ']', '"'))
        assertEquals(provenance, ProcessingProvenanceCodec().decode(ProcessingProvenanceCodec().encode(provenance)))
    }
}
