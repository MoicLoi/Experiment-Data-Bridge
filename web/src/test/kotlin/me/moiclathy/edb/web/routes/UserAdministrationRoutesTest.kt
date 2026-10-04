package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.put
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
import me.moiclathy.edb.core.database.model.User
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.service.UserAdministrationService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UserAdministrationRoutesTest {
    @Test
    fun `admin changes role while member is forbidden`() = testApplication {
        val service = mockk<UserAdministrationService>()
        every { service.changeRole(1, "root", "alice", UserRole.ADMIN) } returns
            User(2, "alice", "hidden", "Alice", null, null, UserRole.ADMIN, true)

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { userAdministrationRoutes(service) }
        }

        val success = client.put("$PATH/alice/role") {
            bearerAuth(token("root", 1, UserRole.ADMIN))
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"role":"ADMIN"}""")
        }
        val forbidden = client.put("$PATH/alice/role") {
            bearerAuth(token("member", 3, UserRole.MEMBER))
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"role":"ADMIN"}""")
        }

        assertEquals(HttpStatusCode.OK, success.status)
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        verify(exactly = 1) { service.changeRole(1, "root", "alice", UserRole.ADMIN) }
    }

    private fun token(username: String, userId: Int, role: UserRole) = JWT.create()
        .withSubject(username)
        .withClaim("userId", userId)
        .withClaim("role", role.name)
        .sign(ALGORITHM)

    private companion object {
        const val PATH = "/api/v1/admin/users"
        val ALGORITHM: Algorithm = Algorithm.HMAC256("user-administration-test-secret")
    }
}
