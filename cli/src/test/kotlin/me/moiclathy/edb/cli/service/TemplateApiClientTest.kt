package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TemplateApiClientTest {
    @Test
    fun `maps template CRUD and preserves unspecified update fields`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(listOf(
            TaskHttpResponse(200, "[${summaryJson()}]"),
            TaskHttpResponse(200, wrappedTemplate()),
            TaskHttpResponse(201, wrappedTemplate(name = "Created")),
            TaskHttpResponse(200, wrappedTemplate()),
            TaskHttpResponse(200, wrappedTemplate(version = 2, yaml = "version: 2")),
            TaskHttpResponse(204, "")
        ))
        val client = TemplateApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, request -> requests += request; responses.removeFirst() }
        )

        assertEquals(1, client.list("motion study", "experiment", "private").size)
        assertEquals("Research", client.info(5).name)
        client.create("Created", "", "experiment", "private", "version: 1")
        val updated = client.update(5, "version: 2", null, null, null, null)
        client.delete(5)

        assertEquals(2, updated.version)
        assertEquals(listOf("GET", "GET", "POST", "GET", "PUT", "DELETE"), requests.map { it.method })
        assertEquals(
            "/api/v1/templates?keyword=motion%20study&type=EXPERIMENT&visibility=PRIVATE",
            requests[0].path
        )
        val update = Json.parseToJsonElement(requireNotNull(requests[4].body)).jsonObject
        assertEquals("Research", update.getValue("name").jsonPrimitive.content)
        assertEquals("PUBLIC", update.getValue("visibility").jsonPrimitive.content)
        assertEquals("version: 2", update.getValue("yaml").jsonPrimitive.content)
    }

    private fun summaryJson() = """{"id":5,"name":"Research","type":"EXPERIMENT","visibility":"PUBLIC"}"""

    private fun wrappedTemplate(name: String = "Research", version: Int = 1, yaml: String = "version: 1") =
        """{"template":{"id":5,"name":"$name","description":"Description","type":"EXPERIMENT","visibility":"PUBLIC","metadata":{"createdAt":"2026-08-11T10:00:00Z","updatedAt":"2026-08-11T10:00:00Z","author":"alice","version":$version},"yaml":"$yaml"}}"""
}
