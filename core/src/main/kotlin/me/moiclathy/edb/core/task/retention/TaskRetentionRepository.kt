package me.moiclathy.edb.core.task.retention

import java.time.Instant

interface TaskRetentionRepository {
    fun countExpired(cutoff: Instant): Long

    /** Deletes at most [limit] terminal tasks completed before [cutoff]. */
    fun deleteExpired(cutoff: Instant, limit: Int): Int
}
