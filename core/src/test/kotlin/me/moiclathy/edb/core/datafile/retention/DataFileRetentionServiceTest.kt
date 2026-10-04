package me.moiclathy.edb.core.datafile.retention

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class DataFileRetentionServiceTest {
    @Test
    fun `estimates and deletes expired records in bounded batches`() {
        val repository = RecordingRepository(5)
        val clock = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC)
        val service = DataFileRetentionService(
            repository,
            DataFileRetentionPolicy(
                retentionPeriod = Duration.ofDays(90),
                batchSize = 2,
                maxBatchesPerRun = 2
            ),
            clock
        )

        val estimate = service.estimate()
        val result = service.cleanup()

        assertEquals(5, estimate.expiredDeletedFiles)
        assertEquals(Instant.parse("2026-05-12T12:00:00Z"), estimate.cutoff)
        assertEquals(4, result.deletedRecords)
        assertEquals(2, result.completedBatches)
        assertEquals(1, repository.remaining)
    }

    private class RecordingRepository(var remaining: Int) : DataFileRetentionRepository {
        override fun countExpiredDeleted(cutoff: Instant): Long = remaining.toLong()

        override fun deleteExpiredDeleted(cutoff: Instant, limit: Int): Int {
            val deleted = minOf(remaining, limit)
            remaining -= deleted
            return deleted
        }
    }
}
