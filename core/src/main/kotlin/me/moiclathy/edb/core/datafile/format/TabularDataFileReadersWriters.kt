package me.moiclathy.edb.core.datafile.format

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PushbackReader
import java.nio.charset.StandardCharsets
import java.util.Locale

class CsvDataFileReader : DataFileReader<TabularData> {
    override val format = DataFileFormat.CSV
    override val valueType = TabularData::class

    override fun read(input: InputStream, options: Map<String, String>): TabularData {
        val records = CsvRecords(InputStreamReader(input, StandardCharsets.UTF_8))
        val headers = records.read() ?: throw IllegalArgumentException("CSV input is empty.")
        return TabularData(headers, generateSequence(records::read))
    }
}

class CsvDataFileWriter : DataFileWriter<TabularData> {
    override val format = DataFileFormat.CSV
    override val valueType = TabularData::class
    override val mediaType = "text/csv"

    override fun write(value: TabularData, output: OutputStream, options: Map<String, String>) {
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8).buffered()
        writer.appendCsv(value.headers)
        value.rows().forEach { writer.appendCsv(it) }
        writer.flush()
    }
}

class JsonDataFileReader(private val json: Json = Json) : DataFileReader<TabularData> {
    override val format = DataFileFormat.JSON
    override val valueType = TabularData::class

    override fun read(input: InputStream, options: Map<String, String>): TabularData {
        val array = try {
            json.parseToJsonElement(input.bufferedReader(StandardCharsets.UTF_8).readText()) as? JsonArray
                ?: throw IllegalArgumentException("JSON tabular input must be an array of objects.")
        } catch (failure: SerializationException) {
            throw IllegalArgumentException("JSON tabular input is malformed.", failure)
        }
        require(array.isNotEmpty()) { "JSON tabular input must contain at least one object." }
        val objects = array.mapIndexed { index, element ->
            element as? JsonObject ?: throw IllegalArgumentException("JSON row ${index + 1} must be an object.")
        }
        val headers = linkedSetOf<String>()
        objects.forEach { headers += it.keys }
        return TabularData(headers.toList(), objects.asSequence().map { row ->
            headers.map { header -> row[header].toTabularValue() }
        })
    }
}

class JsonDataFileWriter(private val json: Json = Json) : DataFileWriter<TabularData> {
    override val format = DataFileFormat.JSON
    override val valueType = TabularData::class
    override val mediaType = "application/json"

    override fun write(value: TabularData, output: OutputStream, options: Map<String, String>) {
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8).buffered()
        writer.append('[')
        var first = true
        value.rows().forEach { row ->
            if (!first) writer.append(',')
            first = false
            val jsonRow = JsonObject(value.headers.indices.associate { value.headers[it] to JsonPrimitive(row[it]) })
            writer.append(json.encodeToString(JsonElement.serializer(), jsonRow))
        }
        writer.append(']')
        writer.flush()
    }
}

class XlsxDataFileReader : DataFileReader<TabularData> {
    override val format = DataFileFormat.XLSX
    override val valueType = TabularData::class

    override fun read(input: InputStream, options: Map<String, String>): TabularData {
        val workbook = WorkbookFactory.create(input)
        try {
            require(workbook.numberOfSheets > 0) { "XLSX input contains no worksheets." }
            val sheetIndex = options["sheetIndex"]?.toIntOrNull() ?: 0
            require(sheetIndex in 0 until workbook.numberOfSheets) { "XLSX sheet index is out of range: $sheetIndex" }
            val sheet = workbook.getSheetAt(sheetIndex)
            val firstRow = sheet.getRow(sheet.firstRowNum)
                ?: throw IllegalArgumentException("XLSX worksheet is empty.")
            val columnCount = firstRow.lastCellNum.toInt()
            require(columnCount > 0) { "XLSX worksheet has no columns." }
            val formatter = DataFormatter(Locale.ROOT)
            val headers = (0 until columnCount).map { formatter.formatCellValue(firstRow.getCell(it)) }
            val rows = ((sheet.firstRowNum + 1)..sheet.lastRowNum).asSequence().map { rowIndex ->
                val row = sheet.getRow(rowIndex)
                (0 until columnCount).map { formatter.formatCellValue(row?.getCell(it)) }
            }
            return TabularData(headers, rows, workbook::close)
        } catch (failure: Exception) {
            workbook.close()
            throw failure
        }
    }
}

class XlsxDataFileWriter : DataFileWriter<TabularData> {
    override val format = DataFileFormat.XLSX
    override val valueType = TabularData::class
    override val mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    override fun write(value: TabularData, output: OutputStream, options: Map<String, String>) {
        val workbook = SXSSFWorkbook(100)
        try {
            val requested = options["sheetName"]?.takeIf { it.isNotBlank() } ?: "Data"
            val sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(requested))
            val header = sheet.createRow(0)
            value.headers.forEachIndexed { index, cell -> header.createCell(index).setCellValue(cell) }
            value.rows().forEachIndexed { rowIndex, values ->
                val row = sheet.createRow(rowIndex + 1)
                values.forEachIndexed { index, cell -> row.createCell(index).setCellValue(cell) }
            }
            workbook.write(output)
        } finally {
            workbook.close()
        }
    }
}

fun defaultTabularDataFileReaderRegistry(json: Json = Json) = DataFileReaderRegistry(
    listOf(CsvDataFileReader(), JsonDataFileReader(json), XlsxDataFileReader())
)

fun defaultTabularDataFileWriterRegistry(json: Json = Json) = DataFileWriterRegistry(
    listOf(CsvDataFileWriter(), JsonDataFileWriter(json), XlsxDataFileWriter())
)

private fun JsonElement?.toTabularValue(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString()
}

private fun Appendable.appendCsv(values: List<String>) {
    values.forEachIndexed { index, value ->
        if (index > 0) append(',')
        val quoted = value.any { it == ',' || it == '"' || it == '\r' || it == '\n' } ||
            value.firstOrNull()?.isWhitespace() == true || value.lastOrNull()?.isWhitespace() == true
        if (quoted) append('"').append(value.replace("\"", "\"\"")).append('"') else append(value)
    }
    append('\n')
}

private class CsvRecords(reader: InputStreamReader) {
    private val input = PushbackReader(reader, 1)

    fun read(): List<String>? {
        val record = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        var sawInput = false
        while (true) {
            val code = input.read()
            if (code < 0) {
                if (inQuotes) throw IllegalArgumentException("CSV contains an unterminated quoted field.")
                if (!sawInput && record.isEmpty() && field.isEmpty()) return null
                record += field.toString()
                return record
            }
            sawInput = true
            val character = code.toChar()
            if (inQuotes) {
                if (character == '"') {
                    val next = input.read()
                    if (next == '"'.code) field.append('"') else {
                        inQuotes = false
                        if (next >= 0) input.unread(next)
                    }
                } else field.append(character)
                continue
            }
            when (character) {
                '"' -> {
                    require(!fieldStarted && field.isEmpty()) { "CSV quote may only appear at the beginning of a field." }
                    inQuotes = true
                    fieldStarted = true
                }
                ',' -> {
                    record += field.toString()
                    field.clear()
                    fieldStarted = false
                }
                '\r', '\n' -> {
                    if (character == '\r') {
                        val next = input.read()
                        if (next >= 0 && next != '\n'.code) input.unread(next)
                    }
                    record += field.toString()
                    return record
                }
                else -> {
                    field.append(character)
                    fieldStarted = true
                }
            }
        }
    }
}
