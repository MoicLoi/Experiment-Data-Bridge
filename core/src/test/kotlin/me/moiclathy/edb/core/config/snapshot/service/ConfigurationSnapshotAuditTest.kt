package me.moiclathy.edb.core.config.snapshot.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.moiclathy.edb.core.config.snapshot.repository.ConfigurationSnapshotRepository
import me.moiclathy.edb.core.service.AuditService
import org.junit.jupiter.api.Test

class ConfigurationSnapshotAuditTest {
    @Test
    fun `snapshot creation audits numeric user id separately from username`() {
        val repository = mockk<ConfigurationSnapshotRepository>()
        val audit = mockk<AuditService>(relaxed = true)
        val service = ConfigurationSnapshotService(repository, audit)
        every { repository.findLatest(7) } returns null
        every { repository.save(any()) } answers { firstArg<me.moiclathy.edb.core.config.snapshot.model.ConfigurationSnapshot>().copy(id = 42) }

        service.createSnapshot(7, "version: 1", "alice", 11, "baseline")

        verify { audit.recordSnapshotCreate(42, 7, 11) }
    }
}
