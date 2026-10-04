package me.moiclathy.edb.core.datafile.report

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportDefinitionLoaderAndGeneratorTest {
    @Test
    fun `directory loader registers strict report JSON`(@TempDir directory: Path) {
        Files.writeString(
            directory.resolve("summary.report.json"),
            """
                {
                  "schemaVersion": 1,
                  "id": "experiment-summary",
                  "version": 2,
                  "title": "Experiment Summary",
                  "generatorId": "tabular-summary",
                  "outputFormat": "PDF",
                  "outputFileName": "summary.pdf",
                  "defaultParameters": {"sampleRows": "3"}
                }
            """.trimIndent()
        )
        val definitions = ReportDefinitionRegistry()
        val result = DirectoryReportDefinitionLoader(
            parser = JsonReportDefinitionParser(),
            definitions = definitions,
            generators = ReportGeneratorRegistry(listOf(TabularSummaryReportGenerator()))
        ).load(directory)

        assertEquals(listOf("experiment-summary"), result.definitionIds)
        assertEquals(2, definitions.require("experiment-summary").version)
        assertEquals("3", definitions.require("experiment-summary").defaultParameters["sampleRows"])
    }

    @Test
    fun `tabular summary generator reads CSV and XLSX and produces PDF`() = runBlocking {
        val definition = ReportDefinition(
            id = "experiment-summary",
            title = "Experiment Summary",
            generatorId = TabularSummaryReportGenerator.ID,
            outputFormat = DataFileFormat.PDF,
            outputFileName = "summary.pdf"
        )
        val output = TabularSummaryReportGenerator().generate(
            ReportGenerationInput(
                definition = definition,
                sources = listOf(
                    ReportDataFileInput(
                        source(1, DataFileFormat.CSV, "observations.csv"),
                        ByteArrayInputStream("name,value\na,1\nb,2\n".toByteArray())
                    ),
                    ReportDataFileInput(
                        source(2, DataFileFormat.XLSX, "measurements.xlsx"),
                        ByteArrayInputStream(workbookBytes())
                    )
                ),
                parameters = mapOf("sampleRows" to "2")
            )
        )
        output.use {
            val bytes = it.content.readBytes()
            assertTrue(bytes.decodeToString(0, 5).startsWith("%PDF-"))
            assertEquals(
                DataFileMetadataValue.NumberValue(3.toBigDecimal()),
                it.metadata.values["totalRows"]
            )
        }
    }

    private fun workbookBytes(): ByteArray {
        val bytes = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Data")
            sheet.createRow(0).also { row ->
                row.createCell(0).setCellValue("name")
                row.createCell(1).setCellValue("value")
            }
            sheet.createRow(1).also { row ->
                row.createCell(0).setCellValue("c")
                row.createCell(1).setCellValue(3.0)
            }
            workbook.write(bytes)
        }
        return bytes.toByteArray()
    }

    private fun source(id: Int, format: DataFileFormat, name: String) = DataFile(
        id = id,
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
