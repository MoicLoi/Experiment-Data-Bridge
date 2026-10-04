package me.moiclathy.edb.core.datafile.conversion

import me.moiclathy.edb.core.datafile.format.DataFileReaderRegistry
import me.moiclathy.edb.core.datafile.format.DataFileWriterRegistry
import me.moiclathy.edb.core.datafile.format.TabularData
import me.moiclathy.edb.core.datafile.format.defaultTabularDataFileReaderRegistry
import me.moiclathy.edb.core.datafile.format.defaultTabularDataFileWriterRegistry
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import me.moiclathy.edb.core.datafile.model.DataFileMetadataValue
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class CsvToJsonDataFileConverter(
    readers: DataFileReaderRegistry = defaultTabularDataFileReaderRegistry(),
    writers: DataFileWriterRegistry = defaultTabularDataFileWriterRegistry()
) : RegistryTabularDataFileConverter(DataFileFormat.CSV, DataFileFormat.JSON, readers, writers)

class JsonToCsvDataFileConverter(
    readers: DataFileReaderRegistry = defaultTabularDataFileReaderRegistry(),
    writers: DataFileWriterRegistry = defaultTabularDataFileWriterRegistry()
) : RegistryTabularDataFileConverter(DataFileFormat.JSON, DataFileFormat.CSV, readers, writers)

class CsvToXlsxDataFileConverter(
    readers: DataFileReaderRegistry = defaultTabularDataFileReaderRegistry(),
    writers: DataFileWriterRegistry = defaultTabularDataFileWriterRegistry()
) : RegistryTabularDataFileConverter(DataFileFormat.CSV, DataFileFormat.XLSX, readers, writers) {
    override val options = listOf(
        DataFileConverterOption(
            name = "sheetName",
            type = DataFileConverterOptionType.STRING,
            defaultValue = "Data",
            description = "Worksheet name used in the generated workbook."
        )
    )
}

class XlsxToCsvDataFileConverter(
    readers: DataFileReaderRegistry = defaultTabularDataFileReaderRegistry(),
    writers: DataFileWriterRegistry = defaultTabularDataFileWriterRegistry()
) : RegistryTabularDataFileConverter(DataFileFormat.XLSX, DataFileFormat.CSV, readers, writers) {
    override val options = listOf(
        DataFileConverterOption(
            name = "sheetIndex",
            type = DataFileConverterOptionType.INTEGER,
            defaultValue = "0",
            description = "Zero-based worksheet index to export."
        )
    )
}

abstract class RegistryTabularDataFileConverter(
    final override val sourceFormat: DataFileFormat,
    final override val targetFormat: DataFileFormat,
    private val readers: DataFileReaderRegistry,
    private val writers: DataFileWriterRegistry
) : DataFileConverter {
    override suspend fun convert(input: DataFileConverterInput): DataFileConverterOutput {
        val reader = readers.require(sourceFormat, TabularData::class)
        val writer = writers.require(targetFormat, TabularData::class)
        var rowCount = 0L
        val content = temporaryConversionInput(".${targetFormat.extension()}") { path ->
            reader.read(input.content, input.options).use { source ->
                val counted = TabularData(
                    source.headers,
                    source.rows().map { row -> rowCount++; row }
                )
                Files.newOutputStream(path, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                    writer.write(counted, output, input.options)
                }
            }
        }
        return DataFileConverterOutput(
            fileName = convertedFileName(input.source.originalFileName, targetFormat.extension()),
            content = content,
            mediaType = writer.mediaType,
            metadata = DataFileMetadata(
                mapOf("rowCount" to DataFileMetadataValue.NumberValue(rowCount.toBigDecimal()))
            )
        )
    }
}

private fun DataFileFormat.extension(): String = when (this) {
    DataFileFormat.CSV -> "csv"
    DataFileFormat.JSON -> "json"
    DataFileFormat.XLSX -> "xlsx"
    else -> error("No tabular extension for $this")
}
