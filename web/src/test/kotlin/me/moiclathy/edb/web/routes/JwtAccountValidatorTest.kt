package me.moiclathy.edb.web.security

import io.mockk.every
import io.mockk.mockk
import me.moiclathy.edb.core.database.model.User
import me.moiclathy.edb.core.database.repository.UserRepository
import me.moiclathy.edb.core.security.UserRole
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtAccountValidatorTest {
    private val repository = mockk<UserRepository>()
    private val validator = JwtAccountValidator(repository)

    @Test
    fun `accepts only token matching active current account state`() {
        every { repository.findByUsername("alice") } returns user(UserRole.ADMIN, active = true)

        assertTrue(validator.isCurrent("alice", 7, "ADMIN"))
        assertFalse(validator.isCurrent("alice", 7, "MEMBER"))
        assertFalse(validator.isCurrent("alice", 8, "ADMIN"))

        every { repository.findByUsername("alice") } returns user(UserRole.ADMIN, active = false)
        assertFalse(validator.isCurrent("alice", 7, "ADMIN"))
    }

    private fun user(role: UserRole, active: Boolean) = User(
        7, "alice", "hash", "Alice", null, null, role, active
    )
}
