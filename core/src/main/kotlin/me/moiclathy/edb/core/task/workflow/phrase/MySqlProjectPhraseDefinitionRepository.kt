package me.moiclathy.edb.core.task.workflow.phrase

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class MySqlProjectPhraseDefinitionRepository : ProjectPhraseDefinitionRepository {
    override fun save(value: ProjectPhraseDefinition): ProjectPhraseDefinition = transaction {
        val id = ProjectPhraseDefinitionsTable.insert { row -> row[projectId]=value.projectId;row[definitionId]=value.definitionId;row[version]=value.version;row[documentJson]=value.documentJson;row[sha256]=value.sha256;row[createdBy]=value.createdBy;row[createdAt]=value.createdAt }.resultedValues?.single()?.get(ProjectPhraseDefinitionsTable.id)?.value ?: error("Failed to persist phrase definition")
        value.copy(id=id)
    }
    override fun findLatest(projectId: Int, definitionId: String): ProjectPhraseDefinition? = transaction { ProjectPhraseDefinitionsTable.selectAll().where { (ProjectPhraseDefinitionsTable.projectId eq projectId) and (ProjectPhraseDefinitionsTable.definitionId eq definitionId) }.orderBy(ProjectPhraseDefinitionsTable.version to SortOrder.DESC).limit(1).singleOrNull()?.toDomain() }
    override fun find(projectId:Int,definitionId:String,version:Int):ProjectPhraseDefinition?=transaction{ProjectPhraseDefinitionsTable.selectAll().where{(ProjectPhraseDefinitionsTable.projectId eq projectId) and (ProjectPhraseDefinitionsTable.definitionId eq definitionId) and (ProjectPhraseDefinitionsTable.version eq version)}.limit(1).singleOrNull()?.toDomain()}
    override fun list(projectId: Int,offset:Long,limit:Int): List<ProjectPhraseDefinition> = transaction { ProjectPhraseDefinitionsTable.selectAll().where { ProjectPhraseDefinitionsTable.projectId eq projectId }.orderBy(ProjectPhraseDefinitionsTable.createdAt to SortOrder.DESC).limit(limit).offset(offset).map(ResultRow::toDomain) }
    override fun count(projectId:Int):Long=transaction{ProjectPhraseDefinitionsTable.selectAll().where{ProjectPhraseDefinitionsTable.projectId eq projectId}.count()}
    override fun rename(projectId:Int,definitionId:String,newDefinitionId:String):Int=transaction{ProjectPhraseDefinitionsTable.update({(ProjectPhraseDefinitionsTable.projectId eq projectId) and (ProjectPhraseDefinitionsTable.definitionId eq definitionId)}){it[ProjectPhraseDefinitionsTable.definitionId]=newDefinitionId}}
    override fun delete(projectId:Int,definitionId:String,version:Int):Boolean=transaction{ProjectPhraseDefinitionsTable.deleteWhere{(ProjectPhraseDefinitionsTable.projectId eq projectId) and (ProjectPhraseDefinitionsTable.definitionId eq definitionId) and (ProjectPhraseDefinitionsTable.version eq version)}>0}
}

private fun ResultRow.toDomain() = ProjectPhraseDefinition(this[ProjectPhraseDefinitionsTable.id].value,this[ProjectPhraseDefinitionsTable.projectId],this[ProjectPhraseDefinitionsTable.definitionId],this[ProjectPhraseDefinitionsTable.version],this[ProjectPhraseDefinitionsTable.documentJson],this[ProjectPhraseDefinitionsTable.sha256],this[ProjectPhraseDefinitionsTable.createdBy],this[ProjectPhraseDefinitionsTable.createdAt])
