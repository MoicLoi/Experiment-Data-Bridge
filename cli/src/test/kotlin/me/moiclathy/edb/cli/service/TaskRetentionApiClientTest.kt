package me.moiclathy.edb.cli.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TaskRetentionApiClientTest {
    @Test
    fun `maps overview estimate cleanup and latest state to retention endpoints`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(
            listOf(
                TaskHttpResponse(200, overviewJson()),
                TaskHttpResponse(200, estimateJson()),
                TaskHttpResponse(200, cleanupJson()),
                TaskHttpResponse(200, stateJson())
            )
        )
        val client = TaskApiClient(
            TaskServerConfig("http://localhost:8080", "admin-token"),
            TaskHttpTransport { _, request ->
                requests += request
                responses.removeFirst()
            }
        )

        val overview = client.retentionOverview()
        val estimate = client.retentionEstimate()
        val cleanup = client.runRetentionCleanup()
        val latest = client.lastRetentionResult()

        assertEquals(30, overview.policy.retentionDays)
        assertEquals(12, estimate.expiredTasks)
        assertEquals(12, cleanup.deletedTasks)
        assertEquals(12, latest.lastResult?.deletedTasks)
        assertEquals(
            listOf(
                "/api/v1/admin/task-retention",
                "/api/v1/admin/task-retention/estimate",
                "/api/v1/admin/task-retention/cleanup",
                "/api/v1/admin/task-retention/last-result"
            ),
            requests.map { it.path }
        )
        assertEquals(listOf("GET", "GET", "POST", "GET"), requests.map { it.method })
    }

    private fun overviewJson() =
        """{"policy":{"enabled":true,"retentionDays":30,"cleanupIntervalMinutes":60,"batchSize":100,"maxBatchesPerRun":10},"state":${stateJson()}}"""

    private fun estimateJson() =
        """{"cutoff":"2026-07-12T12:00:00Z","expiredTasks":12,"collectedAt":"2026-08-11T12:00:00Z"}"""

    private fun cleanupJson() =
        """{"cutoff":"2026-07-12T12:00:00Z","deletedTasks":12,"completedBatches":2,"completedAt":"2026-08-11T12:01:00Z"}"""

    private fun stateJson() =
        """{"running":false,"lastStartedAt":"2026-08-11T12:00:00Z","lastResult":${cleanupJson()},"lastFailure":null}"""
}
