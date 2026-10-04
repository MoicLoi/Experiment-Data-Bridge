package me.moiclathy.edb.core.datafile.conversion

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabularDataFileConvertersTest {
    @Test
    fun `CSV and JSON conversion preserves quoted and multiline values`() = runBlocking {
        val csv = "name,note\n" +
            "mouse-1,\"left,right\"\n" +
            "mouse-2,\"line 1\nline \"\"2\"\"\""
        val jsonBytes = CsvToJsonDataFileConverter().convert(
            converterInput(source(DataFileFormat.CSV, "observations.csv"), csv.toByteArray())
        ).use { it.content.readBytes() }
        val rows = Json.parseToJsonElement(jsonBytes.decodeToString()) as JsonArray

        assertEquals(2, rows.size)
        assertEquals("left,right", (rows[0] as JsonObject).getValue("note").jsonPrimitive.content)
        assertEquals("line 1\nline \"2\"", (rows[1] as JsonObject).getValue("note").jsonPrimitive.content)

        val roundTrip = JsonToCsvDataFileConverter().convert(
            converterInput(source(DataFileFormat.JSON, "observations.json"), jsonBytes)
        ).use { it.content.readBytes().decodeToString() }

        assertEquals(csv + "\n", roundTrip)
    }

    @Test
    fun `CSV and XLSX conversion uses a real workbook and round trips its first sheet`() = runBlocking {
        val csv = "name,value\nsample,42\n"
        val xlsx = CsvToXlsxDataFileConverter().convert(
            converterInput(
                source(DataFileFormat.CSV, "measurements.csv"),
                csv.toByteArray(),
                mapOf("sheetName" to "Measurements")
            )
        ).use { it.content.readBytes() }

        assertTrue(xlsx.size > 4)
        assertContentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()), xlsx.copyOfRange(0, 2))

        val roundTrip = XlsxToCsvDataFileConverter().convert(
            converterInput(source(DataFileFormat.XLSX, "measurements.xlsx"), xlsx)
        ).use { it.content.readBytes().decodeToString() }

        assertEquals(csv, roundTrip)
    }

    private fun converterInput(
        source: DataFile,
        bytes: ByteArray,
        options: Map<String, String> = emptyMap()
    ) = DataFileConverterInput(source, ByteArrayInputStream(bytes), options)

    private fun source(format: DataFileFormat, name: String) = DataFile(
        id = 1,
        projectId = 7,
        createdByUserId = 11,
        kind = DataFileKind.RAW_DATA,
        format = format,
        status = DataFileStatus.AVAILABLE,
        originalFileName = name,
        provenance = DataFileProvenance(DataFileOrigin.UPLOAD),
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    )
}
