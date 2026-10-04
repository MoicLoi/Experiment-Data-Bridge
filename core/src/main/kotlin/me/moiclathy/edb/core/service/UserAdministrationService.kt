package me.moiclathy.edb.core.service

import me.moiclathy.edb.core.database.model.User
import me.moiclathy.edb.core.database.repository.RoleChangeResult
import me.moiclathy.edb.core.database.repository.UserRepository
import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.core.security.UserRole

class UserAdministrationService(
    private val repository: UserRepository,
    private val auditService: AuditService
) {
    fun changeRole(
        actorUserId: Int,
        actorUsername: String,
        targetUsername: String,
        newRole: UserRole
    ): User {
        require(actorUserId > 0) { "Actor user ID must be greater than zero." }
        require(actorUsername.isNotBlank()) { "Actor username must not be blank." }
        require(targetUsername.isNotBlank()) { "Target username must not be blank." }

        val actor = repository.findByUsername(actorUsername)
            ?: throw UserAdministrationException("USER_NOT_FOUND", "Administrator account not found.")
        if (actor.id != actorUserId || !actor.isActive || actor.role != UserRole.ADMIN) {
            throw UserAdministrationException("PERMISSION_DENIED", "Administrator permission is required.")
        }
        if (actor.username.equals(targetUsername, ignoreCase = true) && newRole != UserRole.ADMIN) {
            throw UserAdministrationException("USER_SELF_DEMOTION", "Administrators cannot remove their own operator role.")
        }

        val previousRole = repository.findByUsername(targetUsername)?.role
            ?: throw UserAdministrationException("USER_NOT_FOUND", "User '$targetUsername' was not found.")
        when (repository.changeRoleSafely(targetUsername, newRole)) {
            RoleChangeResult.NOT_FOUND ->
                throw UserAdministrationException("USER_NOT_FOUND", "User '$targetUsername' was not found.")
            RoleChangeResult.LAST_ACTIVE_ADMIN ->
                throw UserAdministrationException("LAST_ACTIVE_ADMIN", "The last active administrator cannot be demoted.")
            RoleChangeResult.UPDATED -> auditService.record(
                userId = actorUserId,
                action = "USER_ROLE_CHANGED",
                resourceType = "USER",
                resourceId = repository.findIdByUsername(targetUsername),
                details = "username=$targetUsername,from=${previousRole.name},to=${newRole.name}"
            )
            RoleChangeResult.UNCHANGED -> Unit
        }
        return repository.findByUsername(targetUsername)
            ?: throw IllegalStateException("User disappeared after role change: $targetUsername")
    }
}

class UserAdministrationException(code: String, message: String) : EDBException(code, message)
