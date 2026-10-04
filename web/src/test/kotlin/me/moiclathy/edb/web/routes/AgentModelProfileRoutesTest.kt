package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
import me.moiclathy.edb.agent.provider.AgentChatModel
import me.moiclathy.edb.agent.provider.AgentChatModelRegistry
import me.moiclathy.edb.agent.provider.AgentModelProfile
import me.moiclathy.edb.agent.provider.AgentModelProfileCatalogService
import me.moiclathy.edb.agent.provider.AgentModelProviderRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentModelProfileRoutesTest {
    @Test
    fun `authenticated user lists safe model profile metadata`() = testApplication {
        val fake = AgentChatModel { error("Catalog must not initialize or invoke a model.") }
        val registry = AgentChatModelRegistry(
            profiles = listOf(AgentModelProfile(
                id = "local-planner", providerId = "openai-compatible", modelName = "local-model",
                apiKeySecretRef = "PRIVATE_KEY_ENV"
            )),
            providers = AgentModelProviderRegistry(emptyList()),
            builtInModels = mapOf("fake-workflow-planner" to fake)
        )
        val catalog = AgentModelProfileCatalogService(registry, "local-planner")
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) {
                jwt("auth-jwt") { verifier(JWT.require(ALGORITHM).build()); validate { JWTPrincipal(it.payload) } }
            }
            routing { agentModelProfileRoutes(catalog); agentProviderAdminRoutes(catalog) }
        }

        val response = client.get("/api/v1/agent-model-profiles") { bearerAuth(token()) }
        val unauthorized = client.get("/api/v1/agent-model-profiles")
        val providerStatus = client.get("/api/v1/admin/agent-providers") { bearerAuth(token("ADMIN")) }
        val metrics = client.get("/api/v1/admin/agent-model-metrics") { bearerAuth(token("ADMIN")) }
        val forbidden = client.get("/api/v1/admin/agent-providers") { bearerAuth(token("MEMBER")) }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        assertTrue(body.contains("\"defaultProfileId\":\"local-planner\""))
        assertTrue(body.contains("\"providerId\":\"openai-compatible\""))
        assertTrue(body.contains("\"builtIn\":true"))
        assertFalse(body.contains("PRIVATE_KEY_ENV"))
        assertEquals(HttpStatusCode.OK, providerStatus.status)
        assertTrue(providerStatus.bodyAsText().contains("\"apiKeyConfigured\":true"))
        assertFalse(providerStatus.bodyAsText().contains("PRIVATE_KEY_ENV"))
        assertEquals(HttpStatusCode.OK, metrics.status)
        assertTrue(metrics.bodyAsText().contains("\"requests\":0"))
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
    }

    private fun token(role: String? = null) = JWT.create().withSubject("scientist").withClaim("userId", 7)
        .apply { role?.let { withClaim("role", it) } }.sign(ALGORITHM)

    companion object {
        private val ALGORITHM = Algorithm.HMAC256("agent-profile-test-secret")
    }
}
