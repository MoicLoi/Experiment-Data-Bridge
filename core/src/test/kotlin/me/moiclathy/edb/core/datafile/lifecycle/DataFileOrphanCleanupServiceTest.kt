package me.moiclathy.edb.core.datafile.lifecycle

import me.moiclathy.edb.core.datafile.repository.DataFileStorageReferenceIndex
import me.moiclathy.edb.core.datafile.storage.LocalDataFileStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataFileOrphanCleanupServiceTest {
    @Test
    fun `deletes only unreferenced content older than safety window`(@TempDir root: Path) {
        val now = Instant.parse("2026-08-10T12:00:00Z")
        val storage = LocalDataFileStorage(root)
        val retained = "projects/7/data/retained.csv"
        val oldOrphan = "projects/7/data/old-orphan.csv"
        val recentOrphan = "projects/7/data/recent-orphan.csv"
        storage.store(retained, ByteArrayInputStream("retained".toByteArray()))
        storage.store(oldOrphan, ByteArrayInputStream("old".toByteArray()))
        storage.store(recentOrphan, ByteArrayInputStream("recent".toByteArray()))
        setModified(root, retained, now.minus(Duration.ofDays(3)))
        setModified(root, oldOrphan, now.minus(Duration.ofDays(2)))
        setModified(root, recentOrphan, now.minus(Duration.ofHours(1)))
        val service = DataFileOrphanCleanupService(
            storage = storage,
            inventory = storage,
            referenceIndex = DataFileStorageReferenceIndex { provider ->
                assertEquals(LocalDataFileStorage.DEFAULT_PROVIDER, provider)
                setOf(retained)
            },
            policy = DataFileOrphanCleanupPolicy(minimumAge = Duration.ofHours(24)),
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )

        val estimate = service.estimate()
        val result = service.cleanup()

        assertEquals(1, estimate.orphanFiles)
        assertEquals(3, estimate.orphanBytes)
        assertEquals(3, result.scannedFiles)
        assertEquals(1, result.orphanCandidates)
        assertEquals(1, result.deletedFiles)
        assertEquals(3, result.deletedBytes)
        assertEquals(0, result.failures)
        assertTrue(storage.exists(retained))
        assertTrue(storage.exists(recentOrphan))
        assertFalse(storage.exists(oldOrphan))
    }

    private fun setModified(root: Path, key: String, instant: Instant) {
        Files.setLastModifiedTime(root.resolve(key.replace('/', java.io.File.separatorChar)), FileTime.from(instant))
    }
}
