package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ProjectCliSummary(
    val id: Int,
    val name: String,
    val description: String?,
    val ownerId: Int,
    val mode: String,
    val configYaml: String?
)

class ProjectApiClient(
    private val config: TaskServerConfig,
    private val transport: TaskHttpTransport = JdkTaskHttpTransport()
) {
    private val api = TaskApiClient(config, transport)

    fun list(): List<ProjectCliSummary> =
        api.requestElement("GET", PROJECTS_PATH).jsonArray.map { it.jsonObject.toProject() }

    fun get(projectId: Int): ProjectCliSummary {
        positive(projectId)
        return api.requestElement("GET", "$PROJECTS_PATH/$projectId").jsonObject.toProject()
    }

    fun create(name: String, description: String?, mode: String, configYaml: String?): ProjectCliSummary {
        require(name.isNotBlank()) { "Project name must not be blank." }
        val normalizedMode = mode.uppercase()
        require(normalizedMode in MODES) { "Project mode must be PERSONAL or COLLABORATIVE." }
        val body = buildJsonObject {
            put("name", name)
            description?.let { put("description", it) }
            put("mode", normalizedMode)
            configYaml?.let { put("configYaml", it) }
        }
        return api.requestElement("POST", PROJECTS_PATH, body).jsonObject.toProject()
    }

    fun delete(projectId: Int) {
        positive(projectId)
        val response = transport.execute(config, TaskHttpRequest("DELETE", "$PROJECTS_PATH/$projectId"))
        if (response.status !in 200..299) {
            throw TaskCliHttpException(response.status, response.body.take(500).ifBlank { "No response body" })
        }
    }

    fun update(
        projectId: Int,
        name: String?,
        description: String?,
        mode: String?
    ): ProjectCliSummary {
        positive(projectId)
        require(name != null || description != null || mode != null) {
            "Specify at least one of --name, --description, or --mode."
        }
        name?.let { require(it.isNotBlank()) { "Project name must not be blank." } }
        val normalizedMode = mode?.uppercase()
        normalizedMode?.let { require(it in MODES) { "Project mode must be PERSONAL or COLLABORATIVE." } }

        val current = get(projectId)
        val body = buildJsonObject {
            put("name", name ?: current.name)
            put("description", description ?: current.description)
            put("mode", normalizedMode ?: current.mode)
            put("configYaml", current.configYaml)
        }
        return api.requestElement("PUT", "$PROJECTS_PATH/$projectId", body).jsonObject.toProject()
    }

    fun reload(projectId: Int) {
        positive(projectId)
        val response = transport.execute(config, TaskHttpRequest("POST", "$PROJECTS_PATH/$projectId/reload"))
        if (response.status !in 200..299) {
            throw TaskCliHttpException(response.status, response.body.take(500).ifBlank { "No response body" })
        }
    }

    private fun JsonObject.toProject() = ProjectCliSummary(
        id = getValue("id").jsonPrimitive.int,
        name = getValue("name").jsonPrimitive.content,
        description = nullableString("description"),
        ownerId = getValue("ownerId").jsonPrimitive.int,
        mode = getValue("mode").jsonPrimitive.content,
        configYaml = nullableString("configYaml")
    )

    private fun JsonObject.nullableString(name: String) = this[name]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.content

    private fun positive(projectId: Int) = require(projectId > 0) { "Project ID must be greater than zero." }

    companion object {
        private const val PROJECTS_PATH = "/api/v1/projects"
        private val MODES = setOf("PERSONAL", "COLLABORATIVE")
    }
}
