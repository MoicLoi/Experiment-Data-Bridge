package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskAdminApiClientTest {
    @Test
    fun `maps retry scheduler controls and metrics to admin endpoints`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(
            listOf(
                TaskHttpResponse(200, taskJson()),
                TaskHttpResponse(200, schedulerJson("STOPPED", 2, 0)),
                TaskHttpResponse(200, schedulerJson("RUNNING", 2, 2)),
                TaskHttpResponse(200, schedulerJson("STOPPED", 2, 0)),
                TaskHttpResponse(200, schedulerJson("RUNNING", 4, 4)),
                TaskHttpResponse(200, metricsJson())
            )
        )
        val client = TaskApiClient(
            TaskServerConfig("http://localhost:8080", "admin-token"),
            TaskHttpTransport { _, request ->
                requests += request
                responses.removeFirst()
            }
        )

        assertEquals("QUEUED", client.requeue(51).status)
        assertEquals("STOPPED", client.schedulerState().status)
        assertEquals("RUNNING", client.startScheduler().status)
        assertEquals("STOPPED", client.stopScheduler().status)
        assertEquals(4, client.updateSchedulerWorkers(4).desiredWorkerCount)
        assertEquals(12, client.taskMetrics().succeededCount)

        assertEquals(
            listOf(
                "/api/v1/admin/tasks/51/requeue",
                "/api/v1/admin/task-scheduler",
                "/api/v1/admin/task-scheduler/start",
                "/api/v1/admin/task-scheduler/stop",
                "/api/v1/admin/task-scheduler/workers",
                "/api/v1/admin/task-metrics"
            ),
            requests.map { it.path }
        )
        assertEquals(listOf("POST", "GET", "POST", "POST", "PUT", "GET"), requests.map { it.method })
        val workers = Json.parseToJsonElement(requireNotNull(requests[4].body)).jsonObject
        assertEquals("4", workers.getValue("workerCount").jsonPrimitive.content)
        assertFailsWith<IllegalArgumentException> { client.updateSchedulerWorkers(0) }
    }

    private fun schedulerJson(status: String, desired: Int, active: Int) =
        """{"status":"$status","desiredWorkerCount":$desired,"activeWorkerCount":$active,"startedAt":null,"stoppedAt":null,"lastFailure":null}"""

    private fun metricsJson() =
        """{"queueDepth":3,"queuedCount":2,"retryingCount":1,"runningCount":4,"succeededCount":12,"failedCount":2,"timedOutCount":1,"averageExecutionDurationMs":1250.5,"collectedAt":"2026-08-11T12:00:00Z"}"""

    private fun taskJson() =
        """
        {
          "id":51,"projectId":7,"taskType":"pipeline","status":"QUEUED","priority":50,
          "attemptCount":0,"maxAttempts":3,"nextAttemptAt":null,"timeoutMs":null,
          "cancellationRequestedAt":null,"cancellationRequestedBy":null,"progress":null,
          "result":null,"error":null,"createdAt":"2026-08-11T10:00:00Z",
          "updatedAt":"2026-08-11T12:00:00Z","startedAt":null,"completedAt":null
        }
        """.trimIndent()
}
