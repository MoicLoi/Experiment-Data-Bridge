package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.task.workflow.application.WorkflowIrCompilation

@Serializable
data class WorkflowIrCompilationResponse(
    val schemaVersion: Int,
    val workflowId: String,
    val workflowVersion: String,
    val description: String?,
    val stepIds: List<String>,
    val executionOrder: List<String>,
    val registered: Boolean
) {
    companion object {
        fun from(result: WorkflowIrCompilation) = WorkflowIrCompilationResponse(
            schemaVersion = result.schemaVersion,
            workflowId = result.workflowId,
            workflowVersion = result.workflowVersion,
            description = result.description,
            stepIds = result.stepIds,
            executionOrder = result.executionOrder,
            registered = result.registered
        )
    }
}
