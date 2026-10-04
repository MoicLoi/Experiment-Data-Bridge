package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetApplicationService
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetException
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetMemberNotFoundException
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetState
import me.moiclathy.edb.core.worker.docker.DockerWorkerLogOptions
import me.moiclathy.edb.core.worker.docker.DockerWorkerLogs
import me.moiclathy.edb.core.worker.docker.DockerWorkerState
import me.moiclathy.edb.core.worker.docker.DockerWorkerStatus
import me.moiclathy.edb.web.plugin.configureExceptionHandling
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DockerWorkerFleetAdminRoutesTest {
    @Test
    fun `admin manages fleet and reads targeted logs while member is forbidden`() = testApplication {
        val service = mockk<DockerWorkerFleetApplicationService>()
        val worker = DockerWorkerState(
            status = DockerWorkerStatus.HEALTHY,
            containerName = "edb-r-worker",
            endpoint = URI("http://127.0.0.1:18086"),
            containerId = "r-container",
            healthy = true
        )
        val fleet = DockerWorkerFleetState(mapOf("r" to worker))
        coEvery { service.state() } returns fleet
        coEvery { service.start() } returns fleet
        coEvery { service.stop() } returns fleet
        coEvery { service.restart("r") } returns worker
        coEvery { service.logs("r", any()) } returns DockerWorkerLogs("r-container", "ready")

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { dockerWorkerFleetAdminRoutes(service) }
        }

        val state = client.get(PATH) { bearerAuth(token(UserRole.ADMIN)) }
        val start = client.post("$PATH/start") { bearerAuth(token(UserRole.ADMIN)) }
        val stop = client.post("$PATH/stop") { bearerAuth(token(UserRole.ADMIN)) }
        val restart = client.post("$PATH/r/restart") { bearerAuth(token(UserRole.ADMIN)) }
        val logs = client.get("$PATH/r/logs?tail=25&stdout=true&stderr=false&timestamps=false&timeoutMs=9000") {
            bearerAuth(token(UserRole.ADMIN))
        }
        val forbidden = client.get(PATH) { bearerAuth(token(UserRole.MEMBER)) }

        listOf(state, start, stop, restart, logs).forEach { assertEquals(HttpStatusCode.OK, it.status) }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertTrue(state.bodyAsText().contains("\"id\":\"r\""))
        assertTrue(logs.bodyAsText().contains("\"content\":\"ready\""))
        coVerify {
            service.logs(
                "r",
                DockerWorkerLogOptions(
                    tailLines = 25,
                    includeStdout = true,
                    includeStderr = false,
                    includeTimestamps = false,
                    timeout = Duration.ofSeconds(9)
                )
            )
        }
    }

    @Test
    fun `maps missing members and fleet failures to operational errors`() = testApplication {
        val service = mockk<DockerWorkerFleetApplicationService>()
        coEvery { service.restart("missing") } throws DockerWorkerFleetMemberNotFoundException("missing")
        coEvery { service.state() } throws DockerWorkerFleetException(
            "status",
            mapOf("r" to IllegalStateException("engine unavailable"))
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
            routing { dockerWorkerFleetAdminRoutes(service) }
        }

        val unavailable = client.get(PATH) { bearerAuth(token(UserRole.ADMIN)) }
        val missing = client.post("$PATH/missing/restart") { bearerAuth(token(UserRole.ADMIN)) }

        assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status)
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertTrue(unavailable.bodyAsText().contains("DOCKER_WORKER_FLEET_UNAVAILABLE"))
        assertTrue(missing.bodyAsText().contains("DOCKER_WORKER_NOT_FOUND"))
    }

    private fun token(role: UserRole): String = JWT.create()
        .withSubject("operator")
        .withClaim("userId", 11)
        .withClaim("role", role.name)
        .sign(ALGORITHM)

    companion object {
        private const val PATH = "/api/v1/admin/docker-workers"
        private val ALGORITHM = Algorithm.HMAC256("docker-fleet-admin-test-secret")
    }
}
