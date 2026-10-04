package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProjectSnapshotApiClientTest {
    @Test
    fun `maps snapshot creation queries comparison merge and deletion`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(listOf(
            TaskHttpResponse(200, projectJson()),
            TaskHttpResponse(201, snapshotJson(10, 1)),
            TaskHttpResponse(200, "[${snapshotJson(10, 1)},${snapshotJson(11, 2)}]"),
            TaskHttpResponse(200, snapshotJson(11, 2)),
            TaskHttpResponse(200, snapshotJson(10, 1)),
            TaskHttpResponse(200, compareJson()),
            TaskHttpResponse(409, conflictJson()),
            TaskHttpResponse(204, "")
        ))
        val client = ProjectSnapshotApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, request -> requests += request; responses.removeFirst() }
        )

        assertEquals(10, client.create(7, "baseline").id)
        assertEquals(2, client.list(7).size)
        assertEquals(11, client.latest(7).id)
        assertEquals(10, client.info(10).id)
        assertEquals(1, client.compare(7, 10, 11).changes.size)
        assertFalse(client.merge(7, 10, 11, "fail_on_conflict").success)
        client.delete(10)

        assertEquals(
            listOf("GET", "POST", "GET", "GET", "GET", "GET", "POST", "DELETE"),
            requests.map { it.method }
        )
        val createBody = Json.parseToJsonElement(requireNotNull(requests[1].body)).jsonObject
        assertEquals("version: 1", createBody.getValue("yaml").jsonPrimitive.content)
        val mergeBody = Json.parseToJsonElement(requireNotNull(requests[6].body)).jsonObject
        assertEquals("FAIL_ON_CONFLICT", mergeBody.getValue("strategy").jsonPrimitive.content)
    }

    private fun projectJson() =
        """{"id":7,"name":"Research","description":null,"ownerId":11,"mode":"PERSONAL","configYaml":"version: 1"}"""

    private fun snapshotJson(id: Long, version: Int) =
        """{"id":$id,"projectId":7,"version":$version,"metadata":{"createdAt":"2026-08-11T10:00:00Z","createdBy":"alice","description":"baseline"},"yaml":"version: $version"}"""

    private fun compareJson() =
        """{"sourceSnapshotId":10,"targetSnapshotId":11,"sourceVersion":1,"targetVersion":2,"identical":false,"changes":[{"path":"version","type":"CHANGED","sourceValue":1,"targetValue":2}]}"""

    private fun conflictJson() =
        """{"targetSnapshotId":11,"sourceSnapshotId":10,"conflicts":[{"path":"version","baseValue":"0","sourceValue":"1","targetValue":"2","reason":"both changed"}]}"""
}
