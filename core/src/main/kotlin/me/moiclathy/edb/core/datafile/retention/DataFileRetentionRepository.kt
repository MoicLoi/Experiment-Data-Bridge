package me.moiclathy.edb.core.datafile.retention

import java.time.Instant

interface DataFileRetentionRepository {
    fun countExpiredDeleted(cutoff: Instant): Long
    fun deleteExpiredDeleted(cutoff: Instant, limit: Int): Int
}
