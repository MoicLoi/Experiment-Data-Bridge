package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandler
import me.moiclathy.edb.core.datafile.analysis.SubmitAnalysisTaskCommand
import me.moiclathy.edb.core.task.TaskPriority
import java.time.Duration
import kotlinx.serialization.json.JsonObject

@Serializable
data class AnalysisHandlerResponse(
    val id: String,
    val analysisType: String,
    val tool: String,
    val outputFormat: String,
    val parameterContract: AnalysisParameterContractResponse? = null,
    val capability: AnalysisCapabilityMetadataResponse? = null
) {
    companion object {
        fun from(handler: AnalysisHandler) = AnalysisHandlerResponse(
            id = handler.id,
            analysisType = handler.analysisType,
            tool = handler.tool,
            outputFormat = handler.outputFormat.name,
            parameterContract = handler.parameterContract?.let(AnalysisParameterContractResponse::from),
            capability = handler.capabilityMetadata?.let(AnalysisCapabilityMetadataResponse::from)
        )
    }
}

@Serializable
data class AnalysisCapabilityMetadataResponse(
    val sourceType: String,
    val sourceId: String,
    val sourceVersion: String,
    val titleKey: String,
    val descriptionKey: String? = null,
    val cliPath: List<String> = emptyList()
) {
    companion object {
        fun from(value: me.moiclathy.edb.core.datafile.analysis.AnalysisCapabilityMetadata) = AnalysisCapabilityMetadataResponse(
            value.sourceType, value.sourceId, value.sourceVersion, value.titleKey, value.descriptionKey, value.cliPath
        )
    }
}

@Serializable
data class AnalysisParameterContractResponse(
    val id: String,
    val version: Int,
    val title: String,
    val description: String? = null,
    val schema: JsonObject
) {
    companion object {
        fun from(value: me.moiclathy.edb.core.datafile.analysis.AnalysisParameterContract) =
            AnalysisParameterContractResponse(value.id, value.version, value.title, value.description, value.schema)
    }
}

@Serializable
data class SubmitAnalysisTaskRequest(
    val handlerId: String,
    val sourceDataFileIds: Set<Int>,
    val parameters: Map<String, String> = emptyMap(),
    val parameterValues: JsonObject? = null,
    val priority: Int = TaskPriority.NORMAL.value,
    val retry: TaskRetryPolicyRequest = TaskRetryPolicyRequest(),
    val timeoutMs: Long? = null
) {
    fun toCommand(projectId: Int, requestedBy: Int) = SubmitAnalysisTaskCommand(
        projectId = projectId,
        handlerId = handlerId,
        sourceDataFileIds = sourceDataFileIds,
        parameters = parameters,
        parameterValues = parameterValues,
        requestedBy = requestedBy,
        priority = TaskPriority(priority),
        retryPolicy = retry.toDomain(),
        timeout = timeoutMs?.let(Duration::ofMillis)
    )
}
