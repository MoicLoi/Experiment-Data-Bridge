package me.moiclathy.edb.cli.service

data class SubmitTaskRequest(
    val projectId: Int,
    val pipelineId: String,
    val context: Map<String, String> = emptyMap(),
    val priority: Int = 50,
    val maxAttempts: Int = 1,
    val initialDelayMs: Long = 1_000,
    val backoffMultiplier: Double = 2.0,
    val maxDelayMs: Long = 60_000,
    val timeoutMs: Long? = null
)

data class TaskCliProgress(val percent: Int, val message: String?, val updatedAt: String)

data class TaskCliResult(
    val outputPath: String?,
    val contentType: String?,
    val metadata: Map<String, String>
)

data class TaskCliError(
    val code: String,
    val category: String,
    val message: String,
    val retryable: Boolean,
    val details: Map<String, String>,
    val occurredAt: String
)

data class TaskCliSummary(
    val id: Int,
    val projectId: Int,
    val taskType: String,
    val status: String,
    val priority: Int,
    val attemptCount: Int,
    val maxAttempts: Int,
    val nextAttemptAt: String?,
    val timeoutMs: Long?,
    val cancellationRequestedAt: String?,
    val cancellationRequestedBy: Int?,
    val progress: TaskCliProgress?,
    val result: TaskCliResult?,
    val error: TaskCliError?,
    val createdAt: String,
    val updatedAt: String,
    val startedAt: String?,
    val completedAt: String?
)

data class TaskCliPage(
    val items: List<TaskCliSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Long
)

data class TaskCliAuditEvent(
    val id: Long,
    val type: String,
    val fromStatus: String?,
    val toStatus: String?,
    val actorUserId: Int?,
    val details: Map<String, String>,
    val createdAt: String
)

data class TaskCliTimeline(
    val taskId: Int,
    val events: List<TaskCliAuditEvent>
)

data class TaskLifecycleCliEvent(
    val eventId: String?,
    val taskId: Int,
    val projectId: Int,
    val type: String,
    val previousStatus: String?,
    val status: String,
    val attemptCount: Int,
    val details: Map<String, String>,
    val occurredAt: String
) {
    val isTerminal: Boolean get() = status in TERMINAL_STATUSES

    companion object {
        private val TERMINAL_STATUSES = setOf("COMPLETED", "FAILED", "TIMED_OUT", "CANCELLED")
    }
}

data class TaskSchedulerCliState(
    val status: String,
    val desiredWorkerCount: Int,
    val activeWorkerCount: Int,
    val startedAt: String?,
    val stoppedAt: String?,
    val lastFailure: String?
)

data class TaskMetricsCliSnapshot(
    val queueDepth: Long,
    val queuedCount: Long,
    val retryingCount: Long,
    val runningCount: Long,
    val succeededCount: Long,
    val failedCount: Long,
    val timedOutCount: Long,
    val averageExecutionDurationMs: Double?,
    val collectedAt: String
)

data class TaskRetentionCliPolicy(
    val enabled: Boolean,
    val retentionDays: Long,
    val cleanupIntervalMinutes: Long,
    val batchSize: Int,
    val maxBatchesPerRun: Int
)

data class TaskCleanupCliResult(
    val cutoff: String,
    val deletedTasks: Int,
    val completedBatches: Int,
    val completedAt: String
)

data class TaskRetentionCliState(
    val running: Boolean,
    val lastStartedAt: String?,
    val lastResult: TaskCleanupCliResult?,
    val lastFailure: String?
)

data class TaskRetentionCliOverview(
    val policy: TaskRetentionCliPolicy,
    val state: TaskRetentionCliState
)

data class TaskRetentionCliEstimate(
    val cutoff: String,
    val expiredTasks: Long,
    val collectedAt: String
)

data class DockerWorkerCliState(
    val id: String,
    val status: String,
    val containerName: String,
    val endpoint: String,
    val containerId: String?,
    val healthy: Boolean,
    val detail: String?,
    val observedAt: String
)

data class DockerWorkerFleetCliState(
    val healthy: Boolean,
    val observedAt: String,
    val members: List<DockerWorkerCliState>
)

data class DockerWorkerLogsCliResult(
    val workerId: String,
    val containerId: String,
    val content: String,
    val collectedAt: String
)
