package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UserAdministrationApiClientTest {
    @Test
    fun `maps op and deop role changes`() {
        val requests = mutableListOf<TaskHttpRequest>()
        val client = UserAdministrationApiClient(
            TaskServerConfig("http://localhost:8080", "token"),
            TaskHttpTransport { _, request ->
                requests += request
                val role = Json.parseToJsonElement(requireNotNull(request.body))
                    .jsonObject.getValue("role").jsonPrimitive.content
                TaskHttpResponse(200, userJson(role))
            }
        )

        assertEquals("ADMIN", client.op("alice smith").role)
        assertEquals("MEMBER", client.deop("alice smith").role)
        assertEquals(
            listOf(
                "/api/v1/admin/users/alice%20smith/role",
                "/api/v1/admin/users/alice%20smith/role"
            ),
            requests.map { it.path }
        )
        assertEquals(listOf("PUT", "PUT"), requests.map { it.method })
    }

    private fun userJson(role: String) =
        """{"id":2,"username":"alice smith","fullName":"Alice","email":null,"role":"$role","active":true}"""
}
