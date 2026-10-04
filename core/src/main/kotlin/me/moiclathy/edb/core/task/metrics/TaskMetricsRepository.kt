package me.moiclathy.edb.core.task.metrics

import java.time.Instant

interface TaskMetricsRepository {
    fun snapshot(collectedAt: Instant): TaskRuntimeMetrics
}
