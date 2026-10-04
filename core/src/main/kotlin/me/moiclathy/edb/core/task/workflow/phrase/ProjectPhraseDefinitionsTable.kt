package me.moiclathy.edb.core.task.workflow.phrase

import me.moiclathy.edb.core.database.table.ProjectsTable
import me.moiclathy.edb.core.database.table.UsersTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object ProjectPhraseDefinitionsTable : LongIdTable("project_phrase_definitions") {
    val projectId = reference("project_id", ProjectsTable.id)
    val definitionId = varchar("definition_id", 150)
    val version = integer("version")
    val documentJson = text("document_json")
    val sha256 = char("sha256", 64)
    val createdBy = reference("created_by", UsersTable.id)
    val createdAt = timestamp("created_at")
    init { uniqueIndex(projectId, definitionId, version); uniqueIndex(projectId, definitionId, sha256) }
}
