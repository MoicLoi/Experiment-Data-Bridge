package me.moiclathy.edb.agent.application

import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.exception.PermissionDeniedException
import me.moiclathy.edb.core.exception.ProjectNotFoundException
import me.moiclathy.edb.core.security.PermissionChecker

/** Agent actions are delegated user actions and never receive an elevated service identity. */
interface AgentAuthorization {
    fun requireReadable(userId: Int, projectId: Int)
    fun requireWritable(userId: Int, projectId: Int)
}

class CoreAgentAuthorization(
    private val projects: ProjectRepository,
    private val permissions: PermissionChecker
) : AgentAuthorization {
    override fun requireReadable(userId: Int, projectId: Int) {
        val project = projects.findById(projectId) ?: throw ProjectNotFoundException(projectId)
        if (!permissions.requireReadable(userId, project)) throw PermissionDeniedException()
    }

    override fun requireWritable(userId: Int, projectId: Int) {
        val project = projects.findById(projectId) ?: throw ProjectNotFoundException(projectId)
        if (!permissions.requireWritable(userId, project)) throw PermissionDeniedException()
    }
}
