package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import me.moiclathy.edb.core.template.model.Template
import me.moiclathy.edb.core.template.model.TemplateMetadata
import me.moiclathy.edb.core.template.model.TemplateSummary
import me.moiclathy.edb.core.template.model.TemplateType
import me.moiclathy.edb.core.template.model.TemplateVisibility
import me.moiclathy.edb.core.template.service.TemplateService
import me.moiclathy.edb.web.routes.template.templateRoutes
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemplateAuthorizationRoutesTest {
    @Test
    fun `private templates are visible and writable only by author or admin`() = testApplication {
        val service = mockk<TemplateService>()
        val own = template(1, "Own", "alice", TemplateVisibility.PRIVATE)
        val other = template(2, "Other", "bob", TemplateVisibility.PRIVATE)
        val public = template(3, "Public", "bob", TemplateVisibility.PUBLIC)
        every { service.search(any()) } returns listOf(own, other, public).map {
            TemplateSummary(it.id!!, it.name, it.type, it.visibility)
        }
        every { service.load(1) } returns own
        every { service.load(2) } returns other
        every { service.load(3) } returns public
        every { service.update(any()) } answers { firstArg() }
        every { service.delete(2) } just runs

        application {
            install(ContentNegotiation) { jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            } }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { templateRoutes(service) }
        }

        val list = client.get(PATH) { bearerAuth(token("alice", "MEMBER")) }
        val forbiddenRead = client.get("$PATH/2") { bearerAuth(token("alice", "MEMBER")) }
        val ownUpdate = client.put("$PATH/1") {
            bearerAuth(token("alice", "MEMBER"))
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"Own","description":"updated","type":"EXPERIMENT","visibility":"PRIVATE","yaml":"version: 2"}""")
        }
        val adminDelete = client.delete("$PATH/2") { bearerAuth(token("root", "ADMIN")) }

        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("Own"))
        assertTrue(list.bodyAsText().contains("Public"))
        assertFalse(list.bodyAsText().contains("Other"))
        assertEquals(HttpStatusCode.Forbidden, forbiddenRead.status)
        assertEquals(HttpStatusCode.OK, ownUpdate.status)
        assertEquals(HttpStatusCode.NoContent, adminDelete.status)
        verify { service.delete(2) }
    }

    private fun template(id: Long, name: String, author: String, visibility: TemplateVisibility) = Template(
        id, name, "Description", TemplateType.EXPERIMENT, visibility,
        TemplateMetadata(Instant.EPOCH, Instant.EPOCH, author, 1), "version: 1"
    )

    private fun token(username: String, role: String) = JWT.create()
        .withSubject(username)
        .withClaim("userId", if (username == "alice") 11 else 1)
        .withClaim("role", role)
        .sign(ALGORITHM)

    private companion object {
        const val PATH = "/api/v1/templates"
        val ALGORITHM: Algorithm = Algorithm.HMAC256("template-authorization-test-secret")
    }
}
