package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.workflow.SetContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.catalog.WorkflowStepCatalogService
import me.moiclathy.edb.core.task.workflow.schema.BuiltInWorkflowStepSchemas
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepSchemaRegistry
import me.moiclathy.edb.web.plugin.configureExceptionHandling
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowStepCatalogRoutesTest {
    @Test
    fun `authenticated member discovers step metadata and raw schema`() = testApplication {
        val service = WorkflowStepCatalogService(
            WorkflowStepHandlerRegistry(listOf(SetContextWorkflowStepHandler())),
            WorkflowStepSchemaRegistry(BuiltInWorkflowStepSchemas.all())
        )
        application {
            install(ContentNegotiation) { json() }
            configureExceptionHandling()
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { workflowStepCatalogRoutes(service) }
        }

        val list = client.get(PATH) { bearerAuth(token()) }
        val detail = client.get("$PATH/context.set?version=1") { bearerAuth(token()) }
        val schema = client.get("$PATH/context.set/schema?version=1") { bearerAuth(token()) }
        val missing = client.get("$PATH/missing") { bearerAuth(token()) }
        val unauthorized = client.get(PATH)

        assertEquals(HttpStatusCode.OK, list.status)
        assertEquals(HttpStatusCode.OK, detail.status)
        assertEquals(HttpStatusCode.OK, schema.status)
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        assertTrue(list.bodyAsText().contains("context.set"))
        assertTrue(detail.bodyAsText().contains("\"schemaAvailable\":true"))
        assertTrue(detail.bodyAsText().contains("\"phrasePatterns\""))
        assertTrue(detail.bodyAsText().contains("step-short"))
        assertEquals("application/schema+json", schema.contentType()?.withoutParameters().toString())
        assertTrue(schema.bodyAsText().contains(WORKFLOW_JSON_SCHEMA_DIALECT))
    }

    private fun token() = JWT.create()
        .withSubject("member")
        .withClaim("userId", 12)
        .withClaim("role", UserRole.MEMBER.name)
        .sign(ALGORITHM)

    companion object {
        private const val PATH = "/api/v1/workflow-steps"
        private const val WORKFLOW_JSON_SCHEMA_DIALECT = "https://json-schema.org/draft/2020-12/schema"
        private val ALGORITHM = Algorithm.HMAC256("workflow-step-catalog-test-secret")
    }
}
