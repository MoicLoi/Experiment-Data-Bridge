package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaskApiClientTest {
    @Test
    fun `maps submit list status and cancel to the server task contract`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val configurations = mutableListOf<TaskServerConfig>()
        val responses = ArrayDeque(
            listOf(
                TaskHttpResponse(201, taskJson("QUEUED")),
                TaskHttpResponse(200, pageJson()),
                TaskHttpResponse(200, taskJson("RUNNING")),
                TaskHttpResponse(200, taskJson("CANCELLED"))
            )
        )
        val transport = TaskHttpTransport { config, request ->
            configurations += config
            requests += request
            responses.removeFirst()
        }
        val client = TaskApiClient(TaskServerConfig("http://localhost:8080/", "secret", 5_000), transport)

        val submitted = client.submit(
            SubmitTaskRequest(
                projectId = 7,
                pipelineId = "import-data",
                context = mapOf("source" to "lab.csv"),
                priority = 75,
                maxAttempts = 3,
                timeoutMs = 10_000
            )
        )
        val page = client.list(7, "queued", 0, 20)
        val status = client.status(51)
        val cancelled = client.cancel(51)

        assertEquals(listOf("POST", "GET", "GET", "POST"), requests.map { it.method })
        assertEquals("/api/v1/tasks/pipeline", requests[0].path)
        assertEquals("/api/v1/projects/7/tasks?status=QUEUED&page=0&size=20", requests[1].path)
        assertEquals("/api/v1/tasks/51", requests[2].path)
        assertEquals("/api/v1/tasks/51/cancel", requests[3].path)
        assertEquals(setOf("secret"), configurations.mapTo(mutableSetOf()) { it.token })
        val body = Json.parseToJsonElement(requireNotNull(requests[0].body)).jsonObject
        assertEquals("import-data", body.getValue("pipelineId").jsonPrimitive.content)
        assertEquals("lab.csv", body.getValue("context").jsonObject.getValue("source").jsonPrimitive.content)
        assertEquals("QUEUED", submitted.status)
        assertEquals(1, page.totalElements)
        assertEquals("RUNNING", status.status)
        assertEquals("CANCELLED", cancelled.status)
    }

    @Test
    fun `classifies server errors and rejects unsafe configuration`() {
        val client = TaskApiClient(
            TaskServerConfig("https://edb.example", "token"),
            TaskHttpTransport { _, _ -> TaskHttpResponse(401, """{"message":"Token expired"}""") }
        )

        val failure = assertFailsWith<TaskCliHttpException> { client.status(51) }
        assertEquals(401, failure.status)
        assertTrue(failure.message.orEmpty().contains("Token expired"))
        assertFailsWith<IllegalArgumentException> { TaskServerConfig("file:///tmp/edb", "token") }
        assertFailsWith<IllegalArgumentException> { TaskServerConfig("http://localhost:8080", "") }
    }

    @Test
    fun `loads the complete task audit timeline`() {
        var request: TaskHttpRequest? = null
        val client = TaskApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, value ->
                request = value
                TaskHttpResponse(
                    200,
                    """
                    {
                      "taskId":51,
                      "events":[{
                        "id":1,
                        "type":"CREATED",
                        "fromStatus":null,
                        "toStatus":"PENDING",
                        "actorUserId":11,
                        "details":{"pipelineId":"import-data"},
                        "createdAt":"2026-08-11T10:00:00Z"
                      }]
                    }
                    """.trimIndent()
                )
            }
        )

        val timeline = client.timeline(51)

        assertEquals("/api/v1/tasks/51/timeline", request?.path)
        assertEquals("CREATED", timeline.events.single().type)
        assertEquals("PENDING", timeline.events.single().toStatus)
    }

    private fun pageJson() =
        """{"items":[${taskJson("QUEUED")}],"page":0,"size":20,"totalElements":1,"totalPages":1}"""

    private fun taskJson(status: String) =
        """
        {
          "id":51,
          "projectId":7,
          "taskType":"pipeline",
          "status":"$status",
          "priority":75,
          "attemptCount":0,
          "maxAttempts":3,
          "nextAttemptAt":null,
          "timeoutMs":10000,
          "cancellationRequestedAt":null,
          "cancellationRequestedBy":null,
          "progress":null,
          "result":null,
          "error":null,
          "createdAt":"2026-08-11T10:00:00Z",
          "updatedAt":"2026-08-11T10:00:00Z",
          "startedAt":null,
          "completedAt":null
        }
        """.trimIndent()
}
