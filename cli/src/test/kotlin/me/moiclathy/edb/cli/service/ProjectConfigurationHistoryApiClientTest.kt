package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProjectConfigurationHistoryApiClientTest {
    @Test
    fun `maps history diff and restore endpoints`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(listOf(
            TaskHttpResponse(200, "[${revisionJson()}]"),
            TaskHttpResponse(200, diffJson()),
            TaskHttpResponse(200, revisionJson("def456", "def4567", "Restore configuration"))
        ))
        val client = ProjectConfigurationHistoryApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, request -> requests += request; responses.removeFirst() }
        )

        assertEquals("abc1234", client.history(7).single().shortCommitId)
        assertEquals("version: 2", client.diff(7, "feature/config", "HEAD~1").newContent)
        assertEquals("def4567", client.restore(7, "abc1234").shortCommitId)

        assertEquals("/api/v1/projects/7/git/history", requests[0].path)
        assertEquals("/api/v1/projects/7/git/diff?from=feature%2Fconfig&to=HEAD%7E1", requests[1].path)
        assertEquals("/api/v1/projects/7/git/restore", requests[2].path)
        assertEquals(listOf("GET", "GET", "POST"), requests.map { it.method })
        assertEquals(
            "abc1234",
            Json.parseToJsonElement(requireNotNull(requests[2].body))
                .jsonObject.getValue("commitId").jsonPrimitive.content
        )
    }

    private fun revisionJson(
        commitId: String = "abc123456",
        shortCommitId: String = "abc1234",
        message: String = "Update configuration"
    ) = """{"commitId":"$commitId","shortCommitId":"$shortCommitId","message":"$message","author":"researcher","time":"2026-08-11T10:00:00Z","email":"researcher@example.com"}"""

    private fun diffJson() =
        """{"oldCommit":"feature/config","newCommit":"HEAD~1","oldContent":"version: 1","newContent":"version: 2"}"""
}
