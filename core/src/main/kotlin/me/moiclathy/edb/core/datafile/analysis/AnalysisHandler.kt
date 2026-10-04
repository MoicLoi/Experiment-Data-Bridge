package me.moiclathy.edb.core.datafile.analysis

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileMetadata
import kotlinx.serialization.json.JsonObject
import java.io.InputStream
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhrasePattern

data class AnalysisDataFileInput(
    val dataFile: DataFile,
    val content: InputStream
)

data class AnalysisHandlerInput(
    val sources: List<AnalysisDataFileInput>,
    val parameters: Map<String, String>
)

data class AnalysisHandlerOutput(
    val fileName: String,
    val content: InputStream,
    val mediaType: String? = null,
    val metadata: DataFileMetadata = DataFileMetadata.EMPTY,
    val outputFormat: DataFileFormat? = null
) : AutoCloseable {
    init {
        require(fileName.isNotBlank()) { "Analysis output file name must not be blank." }
        require(mediaType == null || mediaType.isNotBlank()) { "Analysis output media type must not be blank." }
        require(outputFormat != DataFileFormat.UNKNOWN) { "Analysis output format must be known when supplied." }
    }

    override fun close() = content.close()
}

/** Implementations may delegate to an in-process engine, Python, MATLAB, or another worker runtime. */
interface AnalysisHandler {
    val id: String
    val analysisType: String
    val tool: String
    val outputFormat: DataFileFormat
    val parameterContract: AnalysisParameterContract? get() = null
    val capabilityMetadata: AnalysisCapabilityMetadata? get() = null

    fun encodeParameterValues(values: JsonObject): Map<String, String> =
        throw IllegalArgumentException("Analysis handler '$id' does not expose a structured parameter contract.")

    suspend fun analyze(input: AnalysisHandlerInput): AnalysisHandlerOutput
}

data class AnalysisCapabilityMetadata(
    val sourceType: String,
    val sourceId: String,
    val sourceVersion: String,
    val titleKey: String,
    val descriptionKey: String? = null,
    val cliPath: List<String> = emptyList(),
    val phrasePatterns: List<WorkflowPhrasePattern> = emptyList()
) {
    init {
        require(sourceType.isNotBlank() && sourceId.isNotBlank() && sourceVersion.isNotBlank())
        require(titleKey.isNotBlank())
    }
}
