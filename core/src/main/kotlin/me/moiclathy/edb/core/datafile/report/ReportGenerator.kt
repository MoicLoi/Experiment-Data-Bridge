package me.moiclathy.edb.core.datafile.report

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import java.io.InputStream

data class ReportDataFileInput(
    val dataFile: DataFile,
    val content: InputStream
)

data class ReportGenerationInput(
    val definition: ReportDefinition,
    val sources: List<ReportDataFileInput>,
    val parameters: Map<String, String>
)

data class ReportGeneratorOutput(
    val content: InputStream,
    val fileName: String? = null,
    val mediaType: String? = null,
    val metadata: DataFileMetadata = DataFileMetadata.EMPTY
) : AutoCloseable {
    init {
        require(fileName == null || fileName.isNotBlank()) { "Report output file name must not be blank." }
        require(mediaType == null || mediaType.isNotBlank()) { "Report output media type must not be blank." }
    }

    override fun close() = content.close()
}

interface ReportGenerator {
    val id: String
    val supportedFormats: Set<DataFileFormat>

    suspend fun generate(input: ReportGenerationInput): ReportGeneratorOutput
}
