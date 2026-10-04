package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

class ProjectConfigurationApiClientTest {
    @Test
    fun `maps yaml configuration endpoints`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(listOf(
            TaskHttpResponse(200, projectJson()),
            TaskHttpResponse(200, """{"commitId":"abc"}"""),
            TaskHttpResponse(200, """{"valid":false,"errors":[{"field":"version","message":"required"}]}"""),
            TaskHttpResponse(200, """{"imported":true}""")
        ))
        val client = ProjectConfigurationApiClient(
            config = TaskServerConfig("http://localhost:8080", "token"),
            jsonTransport = TaskHttpTransport { _, request -> requests += request; responses.removeFirst() },
            binaryTransport = FakeBinaryTransport()
        )

        assertEquals("version: 1", client.show(7))
        assertEquals("abc", client.update(7, "version: 2").getValue("commitId").jsonPrimitive.content)
        val validation = client.validate(7, "invalid: true")
        assertEquals(false, validation.valid)
        assertEquals("version", validation.errors.single().field)
        assertEquals(true, client.importYaml(7, "version: 3").getValue("imported").jsonPrimitive.content.toBoolean())

        assertEquals(
            listOf(
                "/api/v1/projects/7",
                "/api/v1/projects/7/configuration",
                "/api/v1/projects/7/configuration/validate",
                "/api/v1/projects/7/configuration/import"
            ),
            requests.map { it.path }
        )
        assertEquals(listOf("GET", "PUT", "POST", "POST"), requests.map { it.method })
        assertEquals(
            "version: 2",
            Json.parseToJsonElement(requireNotNull(requests[1].body)).jsonObject.getValue("yaml").jsonPrimitive.content
        )
    }

    @Test
    fun `maps yaml and edb binary endpoints`() {
        val binary = FakeBinaryTransport()
        val client = ProjectConfigurationApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, _ -> error("JSON transport should not be used") },
            binary
        )

        client.export(7, ProjectConfigurationFileFormat.YAML, Path.of("project.yaml"), false)
        client.export(7, ProjectConfigurationFileFormat.EDB, Path.of("project.edb"), true)
        assertEquals(true, client.importPackage(7, Path.of("project.edb")).getValue("imported").jsonPrimitive.content.toBoolean())

        assertEquals(
            listOf("/api/v1/projects/7/configuration/export", "/api/v1/projects/7/package"),
            binary.downloadPaths
        )
        assertEquals(7, binary.uploadProjectId)
        assertEquals("project.edb", binary.uploadFile?.fileName.toString())
    }

    private class FakeBinaryTransport : ProjectConfigurationBinaryTransport {
        val downloadPaths = mutableListOf<String>()
        var uploadProjectId: Int? = null
        var uploadFile: Path? = null

        override fun download(
            config: TaskServerConfig,
            path: String,
            target: Path,
            force: Boolean
        ): ProjectConfigurationDownloadResult {
            downloadPaths += path
            return ProjectConfigurationDownloadResult(target, 42, "application/octet-stream")
        }

        override fun uploadPackage(config: TaskServerConfig, projectId: Int, file: Path): TaskHttpResponse {
            uploadProjectId = projectId
            uploadFile = file
            return TaskHttpResponse(200, """{"imported":true}""")
        }
    }

    private fun projectJson() =
        """{"id":7,"name":"Research","description":null,"ownerId":11,"mode":"PERSONAL","configYaml":"version: 1"}"""
}
