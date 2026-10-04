package me.moiclathy.edb.web.security

import me.moiclathy.edb.core.database.repository.UserRepository
import me.moiclathy.edb.core.security.UserRole

class JwtAccountValidator(private val repository: UserRepository) {
    fun isCurrent(subject: String?, userId: Int?, roleName: String?): Boolean {
        val username = subject ?: return false
        val id = userId ?: return false
        val role = roleName?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: return false
        val user = repository.findByUsername(username) ?: return false
        return user.isActive && user.id == id && user.role == role
    }
}
