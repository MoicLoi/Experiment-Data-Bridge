package me.moiclathy.edb.core.task.workflow.definition

import java.time.Instant

data class ProjectWorkflowDefinition(
    val id: Long? = null,
    val projectId: Int,
    val workflowId: String,
    val version: Int,
    val workflowIrJson: String,
    val workflowSha256: String,
    val createdBy: Int,
    val sourceReference: String? = null,
    val createdAt: Instant
) {
    init {
        require(id == null || id > 0) { "Workflow definition id must be greater than zero." }
        require(projectId > 0) { "Project id must be greater than zero." }
        require(workflowId.isNotBlank()) { "Workflow id must not be blank." }
        require(version > 0) { "Workflow definition version must be greater than zero." }
        require(workflowIrJson.isNotBlank()) { "Workflow IR must not be blank." }
        require(workflowSha256.matches(Regex("[0-9a-f]{64}"))) { "Workflow SHA-256 must be lowercase hexadecimal." }
        require(createdBy > 0) { "Workflow definition creator must be greater than zero." }
        require(sourceReference == null || sourceReference.isNotBlank()) { "Source reference must not be blank." }
    }
}
