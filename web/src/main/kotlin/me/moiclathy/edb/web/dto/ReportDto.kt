package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.datafile.report.ReportDefinition
import me.moiclathy.edb.core.datafile.report.SubmitReportTaskCommand
import me.moiclathy.edb.core.task.TaskPriority
import java.time.Duration

@Serializable
data class ReportDefinitionResponse(
    val id: String,
    val version: Int,
    val title: String,
    val generatorId: String,
    val outputFormat: String,
    val outputFileName: String,
    val defaultParameters: Map<String, String>
) {
    companion object {
        fun from(definition: ReportDefinition) = ReportDefinitionResponse(
            id = definition.id,
            version = definition.version,
            title = definition.title,
            generatorId = definition.generatorId,
            outputFormat = definition.outputFormat.name,
            outputFileName = definition.outputFileName,
            defaultParameters = definition.defaultParameters
        )
    }
}

@Serializable
data class SubmitReportTaskRequest(
    val definitionId: String,
    val sourceDataFileIds: Set<Int> = emptySet(),
    val parameters: Map<String, String> = emptyMap(),
    val priority: Int = TaskPriority.NORMAL.value,
    val retry: TaskRetryPolicyRequest = TaskRetryPolicyRequest(),
    val timeoutMs: Long? = null
) {
    fun toCommand(projectId: Int, requestedBy: Int) = SubmitReportTaskCommand(
        projectId = projectId,
        definitionId = definitionId,
        sourceDataFileIds = sourceDataFileIds,
        parameters = parameters,
        requestedBy = requestedBy,
        priority = TaskPriority(priority),
        retryPolicy = retry.toDomain(),
        timeout = timeoutMs?.let(Duration::ofMillis)
    )
}
