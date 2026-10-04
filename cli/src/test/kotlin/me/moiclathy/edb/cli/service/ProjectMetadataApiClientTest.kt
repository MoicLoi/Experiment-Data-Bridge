package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProjectMetadataApiClientTest {
    @Test
    fun `updates existing metadata while preserving tags and unspecified fields`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(listOf(
            TaskHttpResponse(200, metadataJson()),
            TaskHttpResponse(200, metadataJson(description = "Updated"))
        ))
        val client = client(requests, responses)

        val result = client.set(7, description = "Updated", category = null, dataSource = null)

        assertEquals("Updated", result.description)
        assertEquals(listOf("GET", "PUT"), requests.map { it.method })
        val body = Json.parseToJsonElement(requireNotNull(requests[1].body)).jsonObject
        assertEquals("research", body.getValue("category").jsonPrimitive.content)
        assertEquals("raw", body.getValue("dataSource").jsonPrimitive.content)
        assertEquals("motion", body.getValue("tags").toString().trim('[', ']').trim('"'))
    }

    @Test
    fun `creates missing metadata and maps tag endpoints`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(listOf(
            TaskHttpResponse(404, "missing"),
            TaskHttpResponse(201, metadataJson(description = "Created")),
            TaskHttpResponse(200, tagsJson("motion")),
            TaskHttpResponse(200, tagsJson("motion", "pose estimation")),
            TaskHttpResponse(200, tagsJson("motion")),
            TaskHttpResponse(204, "")
        ))
        val client = client(requests, responses)

        client.set(7, "Created", null, null)
        client.listTags(7)
        client.addTag(7, "pose estimation")
        client.removeTag(7, "pose estimation")
        client.delete(7)

        assertEquals(listOf("GET", "POST", "GET", "POST", "DELETE", "DELETE"), requests.map { it.method })
        assertEquals("/api/v1/projects/7/metadata/tags/pose%20estimation", requests[4].path)
    }

    private fun client(requests: MutableList<TaskHttpRequest>, responses: ArrayDeque<TaskHttpResponse>) =
        ProjectMetadataApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, request -> requests += request; responses.removeFirst() }
        )

    private fun metadataJson(description: String = "Original") =
        """{"projectId":7,"description":"$description","category":"research","tags":["motion"],"updatedAt":"2026-08-11T10:00:00Z","dataSource":"raw"}"""

    private fun tagsJson(vararg tags: String) =
        """{"projectId":7,"tags":[${tags.joinToString(",") { "\"$it\"" }}]}"""
}
