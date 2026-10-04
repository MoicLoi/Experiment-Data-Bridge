package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.workflow.SetContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.application.WorkflowIrApplicationService
import me.moiclathy.edb.core.task.workflow.ir.JsonWorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCompiler
import me.moiclathy.edb.core.task.workflow.schema.BuiltInWorkflowStepSchemas
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepSchemaRegistry
import me.moiclathy.edb.web.plugin.configureExceptionHandling
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowIrRoutesTest {
    @Test
    fun `member validates while only admin compiles and registers Workflow IR`() = testApplication {
        val registry = PipelineRegistry()
        val handlers = WorkflowStepHandlerRegistry(listOf(SetContextWorkflowStepHandler()))
        val service = WorkflowIrApplicationService(
            JsonWorkflowIrCodec(),
            WorkflowIrCompiler(handlers, schemas = WorkflowStepSchemaRegistry(BuiltInWorkflowStepSchemas.all())),
            registry
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
            routing { workflowIrRoutes(service) }
        }

        val preview = client.post("$PATH/validate") {
            bearerAuth(token(UserRole.MEMBER)); contentType(ContentType.Application.Json); setBody(DOCUMENT)
        }
        val forbidden = client.post("$PATH/compile") {
            bearerAuth(token(UserRole.MEMBER)); contentType(ContentType.Application.Json); setBody(DOCUMENT)
        }
        val compiled = client.post("$PATH/compile") {
            bearerAuth(token(UserRole.ADMIN)); contentType(ContentType.Application.Json); setBody(DOCUMENT)
        }
        val conflict = client.post("$PATH/compile") {
            bearerAuth(token(UserRole.ADMIN)); contentType(ContentType.Application.Json); setBody(DOCUMENT)
        }

        assertEquals(HttpStatusCode.OK, preview.status)
        assertTrue(preview.bodyAsText().contains("\"registered\":false"))
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals(HttpStatusCode.Created, compiled.status)
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertTrue(conflict.bodyAsText().contains("WORKFLOW_ALREADY_REGISTERED"))
    }

    private fun token(role: UserRole) = JWT.create()
        .withSubject(role.name.lowercase()).withClaim("userId", 1).withClaim("role", role.name).sign(ALGORITHM)

    companion object {
        private const val PATH = "/api/v1/workflows"
        private val ALGORITHM = Algorithm.HMAC256("workflow-ir-route-test-secret")
        private val DOCUMENT = """
            {"schemaVersion":1,"id":"http-flow","version":"1","steps":[
              {"id":"set","type":"context.set","version":"1","order":0,"parameters":{"key":"x","value":"y"}}
            ]}
        """.trimIndent()
    }
}
