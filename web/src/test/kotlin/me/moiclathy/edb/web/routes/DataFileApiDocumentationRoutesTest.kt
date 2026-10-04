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

class DataFileApiDocumentationRoutesTest {
    @Test
    fun `serves unified DataFile OpenAPI contract and documentation`() = testApplication {
        application { routing { dataFileApiDocumentationRoutes() } }

        val contract = client.get("/api/v1/openapi/datafile-0.25.yaml")
        val body = contract.bodyAsText()
        assertEquals(HttpStatusCode.OK, contract.status)
        assertEquals("application/yaml", contract.contentType()?.withoutParameters().toString())
        val yamlFactory = Class.forName("com.fasterxml.jackson.dataformat.yaml.YAMLFactory")
            .getDeclaredConstructor().newInstance() as JsonFactory
        val document = ObjectMapper(yamlFactory).readTree(body)
        assertEquals("3.0.3", document["openapi"].asText())
        assertEquals(19, document["paths"].size())
        assertTrue(document["paths"].has("/projects/{projectId}/data-files/{dataFileId}/conversions"))
        assertTrue(document["paths"].has("/projects/{projectId}/reports"))
        assertTrue(document["paths"].has("/admin/data-file-orphan-cleanup/cleanup"))
        assertTrue(document["paths"].has("/admin/data-file-retention/cleanup"))
        assertTrue(document["paths"].has("/projects/{projectId}/analyses"))

        val documentation = client.get("/api/v1/docs/datafile-0.25")
        assertEquals(HttpStatusCode.OK, documentation.status)
        assertTrue(documentation.bodyAsText().contains("DataFile API 0.25"))
        assertTrue(documentation.bodyAsText().contains("/api/v1/openapi/datafile-0.25.yaml"))
    }
}
