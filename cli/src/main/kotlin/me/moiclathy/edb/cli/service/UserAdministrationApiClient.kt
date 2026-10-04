package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class AdminUserCli(
    val id: Int,
    val username: String,
    val fullName: String,
    val email: String?,
    val role: String,
    val active: Boolean
)

class UserAdministrationApiClient(
    config: TaskServerConfig,
    transport: TaskHttpTransport = JdkTaskHttpTransport()
) {
    private val api = TaskApiClient(config, transport)

    fun op(username: String): AdminUserCli = changeRole(username, "ADMIN")
    fun deop(username: String): AdminUserCli = changeRole(username, "MEMBER")

    private fun changeRole(username: String, role: String): AdminUserCli {
        require(username.isNotBlank()) { "Username must not be blank." }
        val result = api.requestElement(
            "PUT",
            "/api/v1/admin/users/${encode(username)}/role",
            buildJsonObject { put("role", role) }
        ).jsonObject
        return AdminUserCli(
            id = result.getValue("id").jsonPrimitive.int,
            username = result.getValue("username").jsonPrimitive.content,
            fullName = result.getValue("fullName").jsonPrimitive.content,
            email = result["email"]?.jsonPrimitive?.contentOrNull,
            role = result.getValue("role").jsonPrimitive.content,
            active = result.getValue("active").jsonPrimitive.boolean
        )
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
