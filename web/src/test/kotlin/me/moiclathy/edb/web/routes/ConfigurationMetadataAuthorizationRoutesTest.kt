package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.config.metadata.model.ConfigurationMetadata
import me.moiclathy.edb.core.config.metadata.service.ConfigurationMetadataService
import me.moiclathy.edb.core.database.model.Project
import me.moiclathy.edb.core.project.ProjectMode
import me.moiclathy.edb.core.service.ProjectService
import me.moiclathy.edb.web.routes.config.configurationMetadataRoutes
import me.moiclathy.edb.web.routes.config.configurationMetadataTagRoutes
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class ConfigurationMetadataAuthorizationRoutesTest {
    @Test
    fun `metadata and tag routes enforce project read and write authorization`() = testApplication {
        val metadataService = mockk<ConfigurationMetadataService>()
        val projectService = mockk<ProjectService>()
        val project = Project(7, "Research", null, 11, ProjectMode.PERSONAL, null)
        val metadata = ConfigurationMetadata(7, "Description", "research", listOf("motion"), Instant.EPOCH, "raw")
        every { projectService.getProject(7, 11) } returns project
        every { projectService.requireWritableProject(7, 11) } returns project
        every { metadataService.get(7) } returns metadata
        every { metadataService.listTags(7) } returns metadata.tags
        every { metadataService.addTag(7, "pose") } returns metadata.copy(tags = listOf("motion", "pose"))
        every { metadataService.removeTag(7, "motion") } returns metadata.copy(tags = emptyList())

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing {
                configurationMetadataRoutes(metadataService, projectService)
                configurationMetadataTagRoutes(metadataService, projectService)
            }
        }

        val metadataRead = client.get("$BASE/metadata") { bearerAuth(TOKEN) }
        val tagsRead = client.get("$BASE/metadata/tags") { bearerAuth(TOKEN) }
        val tagAdd = client.post("$BASE/metadata/tags") {
            bearerAuth(TOKEN)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"tag":"pose"}""")
        }
        val tagRemove = client.delete("$BASE/metadata/tags/motion") { bearerAuth(TOKEN) }

        assertEquals(HttpStatusCode.OK, metadataRead.status)
        assertEquals(HttpStatusCode.OK, tagsRead.status)
        assertEquals(HttpStatusCode.OK, tagAdd.status)
        assertEquals(HttpStatusCode.OK, tagRemove.status)
        verify(exactly = 2) { projectService.getProject(7, 11) }
        verify(exactly = 2) { projectService.requireWritableProject(7, 11) }
    }

    private companion object {
        const val BASE = "/api/v1/projects/7"
        val ALGORITHM: Algorithm = Algorithm.HMAC256("metadata-authorization-test-secret")
        val TOKEN: String = JWT.create()
            .withSubject("owner")
            .withClaim("userId", 11)
            .withClaim("role", "MEMBER")
            .sign(ALGORITHM)
    }
}
