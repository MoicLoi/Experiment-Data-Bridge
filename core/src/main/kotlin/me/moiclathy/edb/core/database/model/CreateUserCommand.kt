package me.moiclathy.edb.core.database.model

import me.moiclathy.edb.core.security.UserRole

data class CreateUserCommand(

    val username: String,

    val passwordHash: String,

    val fullName: String,

    val email: String?,

    val employeeId: String? = null,

    val role: UserRole = UserRole.MEMBER,

    val isActive: Boolean = true

)