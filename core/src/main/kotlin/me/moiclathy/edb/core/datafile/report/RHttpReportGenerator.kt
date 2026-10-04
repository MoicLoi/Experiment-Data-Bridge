package me.moiclathy.edb.core.datafile.report

import me.moiclathy.edb.core.datafile.analysis.AnalysisDataFileInput
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandlerInput
import me.moiclathy.edb.core.datafile.analysis.ExternalHttpAnalysisHandler
import me.moiclathy.edb.core.datafile.analysis.ExternalHttpAnalysisHandlerConfig
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import java.net.URI
import java.time.Duration

data class RHttpReportGeneratorConfig(
    val id: String = "r-tabular-summary-report",
    val endpoint: URI,
    val supportedFormats: Set<DataFileFormat> = setOf(DataFileFormat.PDF),
    val bearerToken: String? = null,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val requestTimeout: Duration = Duration.ofMinutes(5)
) {
    init {
        require(id.isNotBlank()) { "R HTTP report generator id must not be blank." }
        require(supportedFormats.isNotEmpty() && DataFileFormat.UNKNOWN !in supportedFormats) {
            "R HTTP report generator formats must be known and not empty."
        }
    }
}

class RHttpReportGenerator(
    private val config: RHttpReportGeneratorConfig,
    private val delegate: ExternalHttpAnalysisHandler = ExternalHttpAnalysisHandler(
        ExternalHttpAnalysisHandlerConfig(
            id = config.id,
            analysisType = "report",
            tool = "r-http-report",
            endpoint = config.endpoint,
            outputFormat = config.supportedFormats.first(),
            bearerToken = config.bearerToken,
            connectTimeout = config.connectTimeout,
            requestTimeout = config.requestTimeout,
            errorCodePrefix = "R_REPORT"
        )
    )
) : ReportGenerator {
    override val id: String = config.id
    override val supportedFormats: Set<DataFileFormat> = config.supportedFormats

    override suspend fun generate(input: ReportGenerationInput): ReportGeneratorOutput {
        require(input.definition.generatorId == id) {
            "Report definition ${input.definition.id} targets ${input.definition.generatorId}, not $id."
        }
        val protocolParameters = input.parameters + mapOf(
            DEFINITION_ID_PARAMETER to input.definition.id,
            DEFINITION_VERSION_PARAMETER to input.definition.version.toString(),
            TITLE_PARAMETER to input.definition.title,
            OUTPUT_FORMAT_PARAMETER to input.definition.outputFormat.name,
            OUTPUT_FILE_NAME_PARAMETER to input.definition.outputFileName
        )
        val output = delegate.analyze(AnalysisHandlerInput(
            sources = input.sources.map { AnalysisDataFileInput(it.dataFile, it.content) },
            parameters = protocolParameters
        ))
        return try {
            ReportGeneratorOutput(
                content = output.content,
                fileName = output.fileName,
                mediaType = output.mediaType,
                metadata = output.metadata
            )
        } catch (failure: Exception) {
            output.close()
            throw failure
        }
    }

    companion object {
        const val DEFINITION_ID_PARAMETER = "edb.report.definitionId"
        const val DEFINITION_VERSION_PARAMETER = "edb.report.definitionVersion"
        const val TITLE_PARAMETER = "edb.report.title"
        const val OUTPUT_FORMAT_PARAMETER = "edb.report.outputFormat"
        const val OUTPUT_FILE_NAME_PARAMETER = "edb.report.outputFileName"
    }
}
