package me.moiclathy.edb.cli.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DataFileLifecycleApiClientTest {
    @Test
    fun `maps delete orphan cleanup and retention management endpoints`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(
            listOf(
                TaskHttpResponse(200, deletedDataFileJson()),
                TaskHttpResponse(200, orphanOverviewJson()),
                TaskHttpResponse(200, orphanEstimateJson()),
                TaskHttpResponse(200, orphanResultJson()),
                TaskHttpResponse(200, orphanStateJson()),
                TaskHttpResponse(200, retentionOverviewJson()),
                TaskHttpResponse(200, retentionEstimateJson()),
                TaskHttpResponse(200, retentionResultJson()),
                TaskHttpResponse(200, retentionStateJson())
            )
        )
        val client = DataFileApiClient(
            TaskServerConfig("http://localhost:8080", "admin-token"),
            TaskHttpTransport { _, request ->
                requests += request
                responses.removeFirst()
            }
        )

        assertEquals("DELETED", client.delete(7, 81).status)
        assertEquals(24, client.orphanOverview().policy.minimumAgeHours)
        assertEquals(2, client.orphanEstimate().orphanFiles)
        assertEquals(2, client.runOrphanCleanup().deletedFiles)
        assertEquals(2, client.lastOrphanResult().lastResult?.deletedFiles)
        assertEquals(30, client.retentionOverview().policy.retentionDays)
        assertEquals(5, client.retentionEstimate().expiredDeletedFiles)
        assertEquals(5, client.runRetentionCleanup().deletedRecords)
        assertEquals(5, client.lastRetentionResult().lastResult?.deletedRecords)

        assertEquals(
            listOf(
                "/api/v1/projects/7/data-files/81",
                "/api/v1/admin/data-file-orphan-cleanup",
                "/api/v1/admin/data-file-orphan-cleanup/estimate",
                "/api/v1/admin/data-file-orphan-cleanup/cleanup",
                "/api/v1/admin/data-file-orphan-cleanup/last-result",
                "/api/v1/admin/data-file-retention",
                "/api/v1/admin/data-file-retention/estimate",
                "/api/v1/admin/data-file-retention/cleanup",
                "/api/v1/admin/data-file-retention/last-result"
            ),
            requests.map { it.path }
        )
        assertEquals(
            listOf("DELETE", "GET", "GET", "POST", "GET", "GET", "GET", "POST", "GET"),
            requests.map { it.method }
        )
    }

    private fun deletedDataFileJson() =
        """{"id":81,"projectId":7,"createdByUserId":11,"kind":"SOURCE","format":"CSV","status":"DELETED","originalFileName":"lab.csv","mediaType":"text/csv","sizeBytes":10,"sha256":"abc","storageProvider":null,"origin":"UPLOAD","parentDataFileIds":[],"producingTaskId":null,"externalSystem":null,"metadata":{},"createdAt":"2026-08-11T10:00:00Z","updatedAt":"2026-08-11T12:00:00Z"}"""

    private fun orphanResultJson() =
        """{"cutoff":"2026-08-10T12:00:00Z","scannedFiles":20,"orphanCandidates":2,"deletedFiles":2,"deletedBytes":4096,"failures":0,"completedAt":"2026-08-11T12:00:00Z"}"""

    private fun orphanStateJson() =
        """{"running":false,"lastStartedAt":"2026-08-11T11:59:00Z","lastResult":${orphanResultJson()},"lastFailure":null}"""

    private fun orphanOverviewJson() =
        """{"policy":{"enabled":true,"minimumAgeHours":24,"cleanupIntervalMinutes":360,"maxDeletesPerRun":1000},"state":${orphanStateJson()}}"""

    private fun orphanEstimateJson() =
        """{"cutoff":"2026-08-10T12:00:00Z","scannedFiles":20,"orphanFiles":2,"orphanBytes":4096,"collectedAt":"2026-08-11T12:00:00Z"}"""

    private fun retentionResultJson() =
        """{"cutoff":"2026-07-12T12:00:00Z","deletedRecords":5,"completedBatches":1,"completedAt":"2026-08-11T12:00:00Z"}"""

    private fun retentionStateJson() =
        """{"running":false,"lastStartedAt":"2026-08-11T11:59:00Z","lastResult":${retentionResultJson()},"lastFailure":null}"""

    private fun retentionOverviewJson() =
        """{"policy":{"enabled":true,"retentionDays":30,"cleanupIntervalMinutes":1440,"batchSize":100,"maxBatchesPerRun":10},"state":${retentionStateJson()}}"""

    private fun retentionEstimateJson() =
        """{"cutoff":"2026-07-12T12:00:00Z","expiredDeletedFiles":5,"collectedAt":"2026-08-11T12:00:00Z"}"""
}
