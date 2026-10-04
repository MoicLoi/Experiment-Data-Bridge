package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import me.moiclathy.edb.core.config.compare.model.ConfigurationCompareResult
import me.moiclathy.edb.core.config.compare.service.ConfigurationCompareService
import me.moiclathy.edb.core.config.merge.model.ConfigurationMergeResult
import me.moiclathy.edb.core.config.merge.service.ConfigurationMergeService
import me.moiclathy.edb.core.config.snapshot.model.ConfigurationSnapshot
import me.moiclathy.edb.core.config.snapshot.model.SnapshotMetadata
import me.moiclathy.edb.core.config.snapshot.service.ConfigurationSnapshotService
import me.moiclathy.edb.core.database.model.Project
import me.moiclathy.edb.core.project.ProjectMode
import me.moiclathy.edb.core.service.ProjectService
import me.moiclathy.edb.web.routes.config.configurationCompareRoutes
import me.moiclathy.edb.web.routes.config.configurationSnapshotRoutes
import me.moiclathy.edb.web.routes.project.projectConfigurationMergeRoutes
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class ConfigurationSnapshotAuthorizationRoutesTest {
    @Test
    fun `all snapshot operations check project read or write permission before execution`() = testApplication {
        val snapshots = mockk<ConfigurationSnapshotService>()
        val projects = mockk<ProjectService>()
        val compare = mockk<ConfigurationCompareService>()
        val merge = mockk<ConfigurationMergeService>()
        val project = Project(7, "Research", null, 11, ProjectMode.PERSONAL, "version: 1")
        val source = snapshot(10, 1)
        val target = snapshot(11, 2)
        every { projects.getProject(7, 11) } returns project
        every { projects.requireWritableProject(7, 11) } returns project
        every { snapshots.createSnapshot(7, "version: 1", "owner", 11, "baseline") } returns source
        every { snapshots.listSnapshots(7) } returns listOf(source, target)
        every { snapshots.getSnapshotOrNull(10) } returns source
        every { snapshots.getSnapshotOrNull(11) } returns target
        every { snapshots.deleteSnapshot(10, 11) } just runs
        every { compare.compare(10, 11, 11) } returns ConfigurationCompareResult(source, target, emptyList())
        every { merge.mergeSnapshots(11, 10, 11, any()) } returns ConfigurationMergeResult.success(target, source, "version: 2")

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
            routing {
                configurationSnapshotRoutes(snapshots, projects)
                configurationCompareRoutes(compare, snapshots, projects)
                projectConfigurationMergeRoutes(merge, snapshots, projects)
            }
        }

        val create = client.post("$PROJECT_PATH/config/snapshots") { jsonBody("""{"yaml":"version: 1","description":"baseline"}""") }
        val list = client.get("$PROJECT_PATH/config/snapshots") { bearerAuth(TOKEN) }
        val info = client.get("/api/v1/config/snapshots/10") { bearerAuth(TOKEN) }
        val delete = client.delete("/api/v1/config/snapshots/10") { bearerAuth(TOKEN) }
        val comparison = client.get("$PROJECT_PATH/config/snapshots/compare?sourceSnapshotId=10&targetSnapshotId=11") { bearerAuth(TOKEN) }
        val merged = client.post("$PROJECT_PATH/config/merge") {
            jsonBody("""{"sourceSnapshotId":10,"targetSnapshotId":11,"strategy":"FAIL_ON_CONFLICT"}""")
        }

        listOf(create, list, info, delete, comparison, merged).forEach { assertEquals(HttpStatusCode.OK.value / 100, it.status.value / 100) }
        verify(exactly = 3) { projects.getProject(7, 11) }
        verify(exactly = 3) { projects.requireWritableProject(7, 11) }
    }

    private fun snapshot(id: Long, version: Int) = ConfigurationSnapshot(
        id, 7, version, SnapshotMetadata(Instant.EPOCH, "owner", "baseline"), "version: $version"
    )

    private fun io.ktor.client.request.HttpRequestBuilder.jsonBody(body: String) {
        bearerAuth(TOKEN)
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(body)
    }

    private companion object {
        const val PROJECT_PATH = "/api/v1/projects/7"
        val ALGORITHM: Algorithm = Algorithm.HMAC256("snapshot-authorization-test-secret")
        val TOKEN: String = JWT.create()
            .withSubject("owner")
            .withClaim("userId", 11)
            .withClaim("role", "MEMBER")
            .sign(ALGORITHM)
    }
}
