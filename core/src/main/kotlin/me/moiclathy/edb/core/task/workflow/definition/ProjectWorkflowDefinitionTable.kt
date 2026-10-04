package me.moiclathy.edb.core.task.workflow.definition

import me.moiclathy.edb.core.database.table.ProjectsTable
import me.moiclathy.edb.core.database.table.UsersTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object ProjectWorkflowDefinitionsTable : LongIdTable("project_workflow_definitions") {
    val projectId = reference("project_id", ProjectsTable.id)
    val workflowId = varchar("workflow_id", 150)
    val version = integer("version")
    val workflowIrJson = text("workflow_ir_json")
    val workflowSha256 = char("workflow_sha256", 64)
    val createdBy = reference("created_by", UsersTable.id)
    val sourceReference = varchar("source_reference", 100).nullable()
    val createdAt = timestamp("created_at")

    init {
        uniqueIndex(projectId, workflowId, version)
        uniqueIndex(projectId, workflowId, workflowSha256)
    }
}
