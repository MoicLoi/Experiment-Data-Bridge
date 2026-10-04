package me.moiclathy.edb.cli.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskEventStreamClientTest {
    @Test
    fun `filters project stream by task and stops on terminal event`() {
        var requestedPath: String? = null
        val transport = TaskEventStreamTransport { _, path, onLine ->
            requestedPath = path
            listOf(
                ": connected", "",
                "id: 52-1", "event: progress_updated", "data: ${eventJson(52, "RUNNING", "PROGRESS_UPDATED")}", "",
                "id: 51-2", "event: progress_updated", "data: ${eventJson(51, "RUNNING", "PROGRESS_UPDATED")}", "",
                ": heartbeat", "",
                "id: 51-3", "event: completed", "data: ${eventJson(51, "COMPLETED", "COMPLETED")}", ""
            ).forEach(onLine)
        }
        val events = mutableListOf<TaskLifecycleCliEvent>()
        val client = TaskEventStreamClient(TaskServerConfig("http://localhost:8080", "token"), transport)

        val reason = client.watch(projectId = 7, taskId = 51, onEvent = events::add)

        assertEquals("/api/v1/projects/7/tasks/events", requestedPath)
        assertEquals(listOf("PROGRESS_UPDATED", "COMPLETED"), events.map { it.type })
        assertEquals("51-2", events.first().eventId)
        assertEquals(TaskWatchStopReason.TERMINAL, reason)
    }

    @Test
    fun `reports an unexpected server stream close`() {
        val client = TaskEventStreamClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskEventStreamTransport { _, _, onLine ->
                onLine(": connected")
                onLine("")
            }
        )

        assertFailsWith<TaskCliConnectionException> {
            client.watch(projectId = 7, onEvent = {})
        }
    }

    private fun eventJson(taskId: Int, status: String, type: String) =
        """{"taskId":$taskId,"projectId":7,"type":"$type","previousStatus":"RUNNING","status":"$status","attemptCount":1,"details":{"progressPercent":"40"},"occurredAt":"2026-08-11T10:00:00Z"}"""
}
