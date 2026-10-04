package me.moiclathy.edb.core.task.metrics

import java.time.Clock
import java.time.Instant

class TaskMetricsApplicationService(
    private val repository: TaskMetricsRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    fun snapshot(): TaskRuntimeMetrics = repository.snapshot(Instant.now(clock))
}
