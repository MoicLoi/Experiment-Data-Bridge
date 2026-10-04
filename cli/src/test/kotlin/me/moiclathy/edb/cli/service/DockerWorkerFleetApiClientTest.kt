package me.moiclathy.edb.cli.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DockerWorkerFleetApiClientTest {
    @Test
    fun `maps fleet controls member lookup restart and logs`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val responses = ArrayDeque(
            listOf(
                TaskHttpResponse(200, fleetJson()),
                TaskHttpResponse(200, fleetJson()),
                TaskHttpResponse(200, fleetJson()),
                TaskHttpResponse(200, fleetJson()),
                TaskHttpResponse(200, workerJson()),
                TaskHttpResponse(200, logsJson())
            )
        )
        val client = TaskApiClient(
            TaskServerConfig("http://localhost:8080", "admin-token"),
            TaskHttpTransport { _, request -> requests += request; responses.removeFirst() }
        )

        assertEquals(1, client.workerFleet().members.size)
        assertEquals("r", client.worker("r").id)
        assertEquals(true, client.startWorkerFleet().healthy)
        assertEquals(true, client.stopWorkerFleet().healthy)
        assertEquals("HEALTHY", client.restartWorker("r").status)
        assertEquals("plumber ready", client.workerLogs("r", 25, 9_000).content)

        assertEquals(
            listOf(
                "/api/v1/admin/docker-workers",
                "/api/v1/admin/docker-workers",
                "/api/v1/admin/docker-workers/start",
                "/api/v1/admin/docker-workers/stop",
                "/api/v1/admin/docker-workers/r/restart",
                "/api/v1/admin/docker-workers/r/logs?tail=25&timeoutMs=9000"
            ),
            requests.map { it.path }
        )
        assertEquals(listOf("GET", "GET", "POST", "POST", "POST", "GET"), requests.map { it.method })
        assertFailsWith<IllegalArgumentException> { client.restartWorker("bad/id") }
        assertFailsWith<IllegalArgumentException> { client.workerLogs("r", 0) }
    }

    private fun fleetJson() =
        """{"healthy":true,"observedAt":"2026-08-11T12:00:00Z","members":[${workerJson()}]}"""

    private fun workerJson() =
        """{"id":"r","status":"HEALTHY","containerName":"edb-r-worker","endpoint":"http://127.0.0.1:18086","containerId":"container-r","healthy":true,"detail":null,"observedAt":"2026-08-11T12:00:00Z"}"""

    private fun logsJson() =
        """{"workerId":"r","containerId":"container-r","content":"plumber ready","collectedAt":"2026-08-11T12:01:00Z"}"""
}
