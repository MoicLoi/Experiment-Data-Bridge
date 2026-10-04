package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionAdminService
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionEstimate
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionManagerState
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionPolicy
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionResult
import me.moiclathy.edb.core.security.UserRole
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class DataFileRetentionAdminRoutesTest {
    @Test
    fun `admin accesses DataFile retention endpoints while member is forbidden`() = testApplication {
        val service = mockk<DataFileRetentionAdminService>()
        val now = Instant.parse("2026-08-10T17:00:00Z")
        val result = DataFileRetentionResult(now, 12, 2, now)
        every { service.policy } returns DataFileRetentionPolicy(enabled = true)
        every { service.state() } returns DataFileRetentionManagerState(lastResult = result)
        coEvery { service.estimate() } returns DataFileRetentionEstimate(now, 12, now)
        coEvery { service.cleanup() } returns result

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { dataFileRetentionAdminRoutes(service) }
        }

        assertEquals(HttpStatusCode.OK, client.get(PATH) { bearerAuth(token(UserRole.ADMIN)) }.status)
        assertEquals(HttpStatusCode.OK, client.get("$PATH/estimate") { bearerAuth(token(UserRole.ADMIN)) }.status)
        assertEquals(HttpStatusCode.OK, client.post("$PATH/cleanup") { bearerAuth(token(UserRole.ADMIN)) }.status)
        assertEquals(HttpStatusCode.OK, client.get("$PATH/last-result") { bearerAuth(token(UserRole.ADMIN)) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get(PATH) { bearerAuth(token(UserRole.MEMBER)) }.status)
    }

    private fun token(role: UserRole): String = JWT.create()
        .withSubject("operator")
        .withClaim("userId", 12)
        .withClaim("role", role.name)
        .sign(ALGORITHM)

    companion object {
        private const val PATH = "/api/v1/admin/data-file-retention"
        private val ALGORITHM = Algorithm.HMAC256("data-file-retention-test-secret")
    }
}
