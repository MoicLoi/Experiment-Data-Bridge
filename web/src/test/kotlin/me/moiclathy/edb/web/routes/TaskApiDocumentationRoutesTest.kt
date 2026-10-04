package me.moiclathy.edb.web.routes

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskApiDocumentationRoutesTest {
    @Test
    fun `serves the versioned OpenAPI contract and offline documentation`() = testApplication {
        application {
            routing { taskApiDocumentationRoutes() }
        }

        val contract = client.get("/api/v1/openapi/task-0.25.yaml")
        val contractBody = contract.bodyAsText()
        assertEquals(HttpStatusCode.OK, contract.status)
        assertEquals("application/yaml", contract.contentType()?.withoutParameters().toString())
        assertTrue(contractBody.contains("openapi: 3.0.3"))
        assertTrue(contractBody.contains("/admin/task-scheduler:"))
        assertTrue(contractBody.contains("/admin/docker-workers:"))
        assertTrue(contractBody.contains("/admin/docker-workers/{workerId}/logs:"))
        assertTrue(contractBody.contains("/workflow-steps/{stepType}/schema:"))
        assertTrue(contractBody.contains("/workflows/validate:"))
        assertTrue(contractBody.contains("/workflows/compile:"))
        assertTrue(contractBody.contains("/admin/task-metrics:"))
        assertTrue(contractBody.contains("/admin/task-retention:"))
        assertTrue(contractBody.contains("text/event-stream"))
        val yamlFactory = Class.forName("com.fasterxml.jackson.dataformat.yaml.YAMLFactory")
            .getDeclaredConstructor()
            .newInstance() as JsonFactory
        val contractDocument = ObjectMapper(yamlFactory).readTree(contractBody)
        assertEquals("3.0.3", contractDocument["openapi"].asText())
        assertEquals(26, contractDocument["paths"].size())

        val documentation = client.get("/api/v1/docs/task-0.25")
        val documentationBody = documentation.bodyAsText()
        assertEquals(HttpStatusCode.OK, documentation.status)
        assertTrue(documentationBody.contains("Task API 0.25"))
        assertTrue(documentationBody.contains("/api/v1/openapi/task-0.25.yaml"))
    }
}
