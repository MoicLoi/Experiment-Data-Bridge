package me.moiclathy.edb.core.task.workflow.phrase

import java.time.Instant

data class ProjectPhraseDefinition(
    val id: Long? = null,
    val projectId: Int,
    val definitionId: String,
    val version: Int,
    val documentJson: String,
    val sha256: String,
    val createdBy: Int,
    val createdAt: Instant
)

interface ProjectPhraseDefinitionRepository {
    fun save(value: ProjectPhraseDefinition): ProjectPhraseDefinition
    fun findLatest(projectId: Int, definitionId: String): ProjectPhraseDefinition?
    fun find(projectId: Int, definitionId: String, version: Int): ProjectPhraseDefinition?
    fun list(projectId: Int, offset: Long, limit: Int): List<ProjectPhraseDefinition>
    fun count(projectId: Int): Long
    fun rename(projectId: Int, definitionId: String, newDefinitionId: String): Int
    fun delete(projectId: Int, definitionId: String, version: Int): Boolean
}
