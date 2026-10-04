package me.moiclathy.edb.core.datafile.analysis

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.DataFileStorage
import java.io.InputStream
import me.moiclathy.edb.core.datafile.provenance.ProcessingProvenanceCodec
import me.moiclathy.edb.core.datafile.provenance.ProcessingProvenanceFactory
import java.time.Clock

data class AnalysisExecutionResult(val artifact: DataFile, val provenanceReport: DataFile)

data class ExecuteAnalysisCommand(
    val projectId: Int,
    val handlerId: String,
    val sourceDataFileIds: Set<Int>,
    val requestedBy: Int,
    val producingTaskId: Int,
    val parameters: Map<String, String> = emptyMap()
) {
    init {
        require(projectId > 0 && requestedBy > 0 && producingTaskId > 0) {
            "Project, requester and producing task ids must be positive."
        }
        require(handlerId.isNotBlank()) { "Analysis handler id must not be blank." }
        require(sourceDataFileIds.isNotEmpty() && sourceDataFileIds.all { it > 0 }) {
            "Analysis requires at least one positive source DataFile id."
        }
        require(parameters.keys.none { it.isBlank() }) { "Analysis parameter names must not be blank." }
    }
}

class AnalysisExecutionService(
    private val handlers: AnalysisHandlerRegistry,
    private val repository: DataFileRepository,
    private val storage: DataFileStorage,
    private val artifactService: AnalysisArtifactService,
    private val clock: Clock = Clock.systemUTC(),
    private val provenanceCodec: ProcessingProvenanceCodec = ProcessingProvenanceCodec()
) {
    suspend fun execute(command: ExecuteAnalysisCommand): AnalysisExecutionResult {
        val startedAt = clock.instant()
        val handler = handlers.require(command.handlerId)
        val sources = command.sourceDataFileIds.sorted().map { id ->
            repository.findById(id)
                ?.takeIf { it.projectId == command.projectId }
                ?: throw AnalysisSourceNotFoundException(id)
        }
        require(sources.all { it.status == DataFileStatus.AVAILABLE }) {
            "All analysis source DataFiles must be AVAILABLE."
        }
        val opened = mutableListOf<InputStream>()
        try {
            val inputs = sources.map { source ->
                val key = requireNotNull(source.storage?.key) {
                    "Analysis source DataFile ${source.id} has no storage reference."
                }
                AnalysisDataFileInput(source, storage.open(key).also(opened::add))
            }
            return handler.analyze(AnalysisHandlerInput(inputs, command.parameters)).use { output ->
                val artifact = artifactService.create(
                    CreateAnalysisArtifactCommand(
                        projectId = command.projectId,
                        sourceDataFileIds = command.sourceDataFileIds,
                        requestedBy = command.requestedBy,
                        producingTaskId = command.producingTaskId,
                        analysisType = handler.analysisType,
                        analysisTool = handler.tool,
                        outputFormat = output.outputFormat ?: handler.outputFormat,
                        outputFileName = output.fileName,
                        content = output.content,
                        mediaType = output.mediaType,
                        metadata = output.metadata
                    )
                )
                val completedAt = clock.instant()
                val operation = command.parameters["operation"] ?: handler.analysisType
                val provenance = ProcessingProvenanceFactory.analysis(
                    operation, command.parameters, output.metadata, handler.id, handler.tool,
                    handler.capabilityMetadata?.sourceVersion ?: "unversioned", command.sourceDataFileIds,
                    requireNotNull(artifact.id), command.producingTaskId, command.requestedBy, startedAt, completedAt
                )
                val report = artifactService.createProcessingReport(CreateProcessingReportCommand(
                    command.projectId, command.sourceDataFileIds, requireNotNull(artifact.id), command.requestedBy,
                    command.producingTaskId, operation, provenanceCodec.encode(provenance)
                ))
                AnalysisExecutionResult(artifact, report)
            }
        } finally {
            opened.asReversed().forEach { runCatching { it.close() } }
        }
    }
}
