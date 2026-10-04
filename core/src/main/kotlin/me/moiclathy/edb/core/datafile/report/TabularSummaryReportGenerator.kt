package me.moiclathy.edb.core.datafile.report

import me.moiclathy.edb.core.datafile.format.DataFileReaderRegistry
import me.moiclathy.edb.core.datafile.format.TabularData
import me.moiclathy.edb.core.datafile.format.defaultTabularDataFileReaderRegistry
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

class TabularSummaryReportGenerator(
    private val readers: DataFileReaderRegistry = defaultTabularDataFileReaderRegistry()
) : ReportGenerator {
    override val id: String = ID
    override val supportedFormats: Set<DataFileFormat> = setOf(DataFileFormat.PDF)

    override suspend fun generate(input: ReportGenerationInput): ReportGeneratorOutput {
        require(input.sources.isNotEmpty()) { "Tabular summary report requires at least one source DataFile." }
        val sampleLimit = input.parameters["sampleRows"]?.toIntOrNull() ?: DEFAULT_SAMPLE_ROWS
        require(sampleLimit in 0..MAX_SAMPLE_ROWS) { "sampleRows must be between 0 and $MAX_SAMPLE_ROWS." }
        val summaries = input.sources.map { source -> summarize(source, sampleLimit, input.parameters) }
        val title = input.parameters["title"]?.takeIf { it.isNotBlank() } ?: input.definition.title
        val content = temporaryPdf { path -> writePdf(path, title, summaries) }
        return ReportGeneratorOutput(
            content = content,
            mediaType = "application/pdf",
            metadata = DataFileMetadata(
                mapOf(
                    "sourceCount" to DataFileMetadataValue.NumberValue(summaries.size.toBigDecimal()),
                    "totalRows" to DataFileMetadataValue.NumberValue(summaries.sumOf { it.rowCount }.toBigDecimal())
                )
            )
        )
    }

    private fun summarize(
        source: ReportDataFileInput,
        sampleLimit: Int,
        parameters: Map<String, String>
    ): TabularSummary {
        require(source.dataFile.format in setOf(DataFileFormat.CSV, DataFileFormat.XLSX)) {
            "Tabular summary supports CSV and XLSX sources, not ${source.dataFile.format}."
        }
        val reader = readers.require(source.dataFile.format, TabularData::class)
        val samples = mutableListOf<List<String>>()
        var rows = 0L
        return reader.read(source.content, parameters).use { data ->
            data.rows().forEach { values ->
                if (samples.size < sampleLimit) samples += values
                rows++
            }
            TabularSummary(source.dataFile.originalFileName, source.dataFile.format, data.headers, rows, samples)
        }
    }

    private fun writePdf(path: Path, title: String, summaries: List<TabularSummary>) {
        PDDocument().use { document ->
            val writer = PdfLineWriter(document)
            writer.line(title, 16f)
            writer.line("Sources: ${summaries.size}", 10f)
            summaries.forEach { summary ->
                writer.blank()
                writer.line("${summary.fileName} (${summary.format})", 13f)
                writer.line("Rows: ${summary.rowCount}  Columns: ${summary.headers.size}", 10f)
                writer.line("Columns: ${summary.headers.joinToString(" | ")}", 9f)
                summary.samples.forEachIndexed { index, row ->
                    writer.line("${index + 1}: ${row.joinToString(" | ")}", 8f)
                }
            }
            writer.close()
            document.save(path.toFile())
        }
    }

    companion object {
        const val ID = "tabular-summary"
        private const val DEFAULT_SAMPLE_ROWS = 5
        private const val MAX_SAMPLE_ROWS = 20
    }
}

private data class TabularSummary(
    val fileName: String,
    val format: DataFileFormat,
    val headers: List<String>,
    val rowCount: Long,
    val samples: List<List<String>>
)

private class PdfLineWriter(private val document: PDDocument) : AutoCloseable {
    private val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private var stream: PDPageContentStream? = null
    private var y = 0f

    init { newPage() }

    fun line(value: String, size: Float) {
        if (y < 50f) newPage()
        val content = requireNotNull(stream)
        content.beginText()
        content.setFont(font, size)
        content.newLineAtOffset(45f, y)
        content.showText(pdfSafe(value).take(MAX_LINE_CHARACTERS))
        content.endText()
        y -= size + 5f
    }

    fun blank() { y -= 8f }

    private fun newPage() {
        stream?.close()
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        stream = PDPageContentStream(document, page)
        y = page.mediaBox.height - 45f
    }

    private fun pdfSafe(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(if (character.code in 32..126 || character.code in 160..255) character else '?')
        }
    }

    override fun close() { stream?.close(); stream = null }

    companion object { private const val MAX_LINE_CHARACTERS = 110 }
}

private fun temporaryPdf(write: (Path) -> Unit): InputStream {
    val path = Files.createTempFile("edb-report-", ".pdf")
    try {
        write(path)
        return object : FilterInputStream(Files.newInputStream(path)) {
            override fun close() {
                try { super.close() } finally { Files.deleteIfExists(path) }
            }
        }
    } catch (failure: Exception) {
        Files.deleteIfExists(path)
        throw failure
    }
}
