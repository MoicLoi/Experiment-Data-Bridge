package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectApiClientTest {
    @Test
    fun `maps project list info create and delete endpoints`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(
            listOf(
                TaskHttpResponse(200, "[${projectJson()}]"),
                TaskHttpResponse(200, projectJson()),
                TaskHttpResponse(201, projectJson(name = "New project", mode = "COLLABORATIVE")),
                TaskHttpResponse(204, "")
            )
        )
        val client = ProjectApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, request ->
                requests += request
                responses.removeFirst()
            }
        )

        assertEquals(1, client.list().size)
        assertEquals("Research", client.get(7).name)
        assertEquals(
            "COLLABORATIVE",
            client.create("New project", "Shared", "collaborative", "version: 1").mode
        )
        client.delete(7)

        assertEquals(
            listOf("/api/v1/projects", "/api/v1/projects/7", "/api/v1/projects", "/api/v1/projects/7"),
            requests.map { it.path }
        )
        assertEquals(listOf("GET", "GET", "POST", "DELETE"), requests.map { it.method })
        val body = Json.parseToJsonElement(requireNotNull(requests[2].body)).jsonObject
        assertEquals("COLLABORATIVE", body.getValue("mode").jsonPrimitive.content)
        assertEquals("version: 1", body.getValue("configYaml").jsonPrimitive.content)
    }

    @Test
    fun `rejects invalid project mode before sending request`() {
        var called = false
        val client = ProjectApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, _ -> called = true; TaskHttpResponse(500, "") }
        )

        assertFailsWith<IllegalArgumentException> { client.create("Research", null, "UNKNOWN", null) }
        assertEquals(false, called)
    }

    @Test
    fun `partial update preserves unspecified project fields and reloads`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(listOf(
            TaskHttpResponse(200, projectJson(configYaml = "version: 1")),
            TaskHttpResponse(200, projectJson(name = "Renamed", mode = "COLLABORATIVE", configYaml = "version: 1")),
            TaskHttpResponse(200, "")
        ))
        val client = ProjectApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, request -> requests += request; responses.removeFirst() }
        )

        val updated = client.update(7, name = "Renamed", description = null, mode = "collaborative")
        client.reload(7)

        assertEquals("Renamed", updated.name)
        assertEquals(listOf("GET", "PUT", "POST"), requests.map { it.method })
        assertEquals(
            listOf("/api/v1/projects/7", "/api/v1/projects/7", "/api/v1/projects/7/reload"),
            requests.map { it.path }
        )
        val body = Json.parseToJsonElement(requireNotNull(requests[1].body)).jsonObject
        assertEquals("Renamed", body.getValue("name").jsonPrimitive.content)
        assertEquals("Project description", body.getValue("description").jsonPrimitive.content)
        assertEquals("COLLABORATIVE", body.getValue("mode").jsonPrimitive.content)
        assertEquals("version: 1", body.getValue("configYaml").jsonPrimitive.content)
    }

    private fun projectJson(
        name: String = "Research",
        mode: String = "PERSONAL",
        configYaml: String? = null
    ): String {
        val yaml = configYaml?.let { "\"$it\"" } ?: "null"
        return """{"id":7,"name":"$name","description":"Project description","ownerId":11,"mode":"$mode","configYaml":$yaml}"""
    }
}
