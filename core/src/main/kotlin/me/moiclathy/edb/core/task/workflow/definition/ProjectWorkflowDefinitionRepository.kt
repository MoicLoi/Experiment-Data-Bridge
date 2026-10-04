package me.moiclathy.edb.core.task.workflow.definition

interface ProjectWorkflowDefinitionRepository {
    fun save(definition: ProjectWorkflowDefinition): ProjectWorkflowDefinition
    fun findById(id: Long): ProjectWorkflowDefinition?
    fun findLatest(projectId: Int, workflowId: String): ProjectWorkflowDefinition?
}
