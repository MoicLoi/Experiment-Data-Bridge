package me.moiclathy.edb.core.task.workflow.definition

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class MySqlProjectWorkflowDefinitionRepository : ProjectWorkflowDefinitionRepository {
    override fun save(definition: ProjectWorkflowDefinition): ProjectWorkflowDefinition = transaction {
        require(definition.id == null) { "A new Workflow definition must not have an id." }
        val id = ProjectWorkflowDefinitionsTable.insert { row ->
            row[projectId] = definition.projectId; row[workflowId] = definition.workflowId
            row[version] = definition.version; row[workflowIrJson] = definition.workflowIrJson
            row[workflowSha256] = definition.workflowSha256; row[createdBy] = definition.createdBy
            row[sourceReference] = definition.sourceReference; row[createdAt] = definition.createdAt
        }.resultedValues?.single()?.get(ProjectWorkflowDefinitionsTable.id)?.value
            ?: error("Failed to persist Workflow definition")
        definition.copy(id = id)
    }

    override fun findById(id: Long): ProjectWorkflowDefinition? = transaction {
        ProjectWorkflowDefinitionsTable.selectAll().where { ProjectWorkflowDefinitionsTable.id eq id }
            .limit(1).singleOrNull()?.toDefinition()
    }

    override fun findLatest(projectId: Int, workflowId: String): ProjectWorkflowDefinition? = transaction {
        ProjectWorkflowDefinitionsTable.selectAll().where {
            (ProjectWorkflowDefinitionsTable.projectId eq projectId) and
                (ProjectWorkflowDefinitionsTable.workflowId eq workflowId)
        }.orderBy(ProjectWorkflowDefinitionsTable.version to SortOrder.DESC)
            .limit(1).singleOrNull()?.toDefinition()
    }
}

private fun ResultRow.toDefinition() = ProjectWorkflowDefinition(
    id = this[ProjectWorkflowDefinitionsTable.id].value, projectId = this[ProjectWorkflowDefinitionsTable.projectId],
    workflowId = this[ProjectWorkflowDefinitionsTable.workflowId], version = this[ProjectWorkflowDefinitionsTable.version],
    workflowIrJson = this[ProjectWorkflowDefinitionsTable.workflowIrJson], workflowSha256 = this[ProjectWorkflowDefinitionsTable.workflowSha256],
    createdBy = this[ProjectWorkflowDefinitionsTable.createdBy], sourceReference = this[ProjectWorkflowDefinitionsTable.sourceReference],
    createdAt = this[ProjectWorkflowDefinitionsTable.createdAt]
)
