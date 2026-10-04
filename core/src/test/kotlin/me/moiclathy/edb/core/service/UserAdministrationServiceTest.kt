package me.moiclathy.edb.core.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.moiclathy.edb.core.database.model.User
import me.moiclathy.edb.core.database.repository.RoleChangeResult
import me.moiclathy.edb.core.database.repository.UserRepository
import me.moiclathy.edb.core.security.UserRole
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserAdministrationServiceTest {
    private val repository = mockk<UserRepository>()
    private val audit = mockk<AuditService>(relaxed = true)
    private val service = UserAdministrationService(repository, audit)

    @Test
    fun `operator grants admin role and records audit`() {
        val actor = user(1, "root", UserRole.ADMIN)
        val before = user(2, "alice", UserRole.MEMBER)
        val after = before.copy(role = UserRole.ADMIN)
        every { repository.findByUsername("root") } returns actor
        every { repository.findByUsername("alice") } returnsMany listOf(before, after)
        every { repository.changeRoleSafely("alice", UserRole.ADMIN) } returns RoleChangeResult.UPDATED
        every { repository.findIdByUsername("alice") } returns 2

        assertEquals(UserRole.ADMIN, service.changeRole(1, "root", "alice", UserRole.ADMIN).role)
        verify {
            audit.record(1, "USER_ROLE_CHANGED", "USER", 2, "username=alice,from=MEMBER,to=ADMIN")
        }
    }

    @Test
    fun `operator cannot demote self even with different username case`() {
        every { repository.findByUsername("root") } returns user(1, "Root", UserRole.ADMIN)

        val failure = assertFailsWith<UserAdministrationException> {
            service.changeRole(1, "root", "ROOT", UserRole.MEMBER)
        }

        assertEquals("USER_SELF_DEMOTION", failure.code)
        verify(exactly = 0) { repository.changeRoleSafely(any(), any()) }
    }

    @Test
    fun `last active administrator cannot be demoted`() {
        every { repository.findByUsername("root") } returns user(1, "root", UserRole.ADMIN)
        every { repository.findByUsername("admin2") } returns user(2, "admin2", UserRole.ADMIN)
        every { repository.changeRoleSafely("admin2", UserRole.MEMBER) } returns RoleChangeResult.LAST_ACTIVE_ADMIN

        val failure = assertFailsWith<UserAdministrationException> {
            service.changeRole(1, "root", "admin2", UserRole.MEMBER)
        }

        assertEquals("LAST_ACTIVE_ADMIN", failure.code)
        verify(exactly = 0) { audit.record(any(), any(), any(), any(), any()) }
    }

    private fun user(id: Int, username: String, role: UserRole, active: Boolean = true) = User(
        id, username, "hash", username, null, null, role, active
    )
}
