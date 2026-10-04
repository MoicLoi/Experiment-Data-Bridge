package me.moiclathy.edb.core.datafile.conversion

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import java.io.InputStream

enum class DataFileConverterOptionType {
    STRING,
    INTEGER
}

data class DataFileConverterOption(
    val name: String,
    val type: DataFileConverterOptionType,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val description: String? = null
) {
    init {
        require(name.isNotBlank()) { "Converter option name must not be blank." }
        require(description == null || description.isNotBlank()) {
            "Converter option description must not be blank."
        }
    }
}

data class DataFileConverterInput(
    val source: DataFile,
    val content: InputStream,
    val options: Map<String, String> = emptyMap()
)

data class DataFileConverterOutput(
    val fileName: String,
    val content: InputStream,
    val mediaType: String? = null,
    val metadata: DataFileMetadata = DataFileMetadata.EMPTY
) : AutoCloseable {
    init {
        require(fileName.isNotBlank()) { "Converted DataFile name must not be blank." }
        require(mediaType == null || mediaType.isNotBlank()) {
            "Converted DataFile media type must not be blank."
        }
    }

    override fun close() = content.close()
}

interface DataFileConverter {
    val sourceFormat: DataFileFormat
    val targetFormat: DataFileFormat
    val options: List<DataFileConverterOption>
        get() = emptyList()

    suspend fun convert(input: DataFileConverterInput): DataFileConverterOutput
}
