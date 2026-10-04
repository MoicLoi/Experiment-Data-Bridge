package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ProjectMetadataCli(
    val projectId: Int,
    val description: String?,
    val category: String?,
    val tags: List<String>,
    val updatedAt: String,
    val dataSource: String?
)

data class ProjectTagsCli(val projectId: Int, val tags: List<String>)

class ProjectMetadataApiClient(
    private val config: TaskServerConfig,
    private val transport: TaskHttpTransport = JdkTaskHttpTransport()
) {
    private val api = TaskApiClient(config, transport)

    fun show(projectId: Int): ProjectMetadataCli {
        positive(projectId)
        return api.requestElement("GET", metadataPath(projectId)).jsonObject.toMetadata()
    }

    fun set(projectId: Int, description: String?, category: String?, dataSource: String?): ProjectMetadataCli {
        positive(projectId)
        require(description != null || category != null || dataSource != null) {
            "Specify at least one of --description, --category, or --data-source."
        }
        val current = try {
            show(projectId)
        } catch (failure: TaskCliHttpException) {
            if (failure.status == 404) null else throw failure
        }
        val body = metadataBody(
            description ?: current?.description,
            category ?: current?.category,
            current?.tags.orEmpty(),
            dataSource ?: current?.dataSource
        )
        val method = if (current == null) "POST" else "PUT"
        return api.requestElement(method, metadataPath(projectId), body).jsonObject.toMetadata()
    }

    fun delete(projectId: Int) {
        positive(projectId)
        executeNoContent("DELETE", metadataPath(projectId))
    }

    fun listTags(projectId: Int): ProjectTagsCli {
        positive(projectId)
        return api.requestElement("GET", tagsPath(projectId)).jsonObject.toTags()
    }

    fun addTag(projectId: Int, tag: String): ProjectTagsCli {
        positive(projectId)
        require(tag.isNotBlank()) { "Tag must not be blank." }
        return api.requestElement(
            "POST", tagsPath(projectId), buildJsonObject { put("tag", tag) }
        ).jsonObject.toTags()
    }

    fun removeTag(projectId: Int, tag: String): ProjectTagsCli {
        positive(projectId)
        require(tag.isNotBlank()) { "Tag must not be blank." }
        return api.requestElement(
            "DELETE", "${tagsPath(projectId)}/${encode(tag)}"
        ).jsonObject.toTags()
    }

    private fun metadataBody(
        description: String?, category: String?, tags: List<String>, dataSource: String?
    ): JsonObject = buildJsonObject {
        put("description", description)
        put("category", category)
        putJsonArray("tags") { tags.forEach { add(JsonPrimitive(it)) } }
        put("dataSource", dataSource)
    }

    private fun JsonObject.toMetadata() = ProjectMetadataCli(
        projectId = getValue("projectId").jsonPrimitive.content.toInt(),
        description = nullableString("description"),
        category = nullableString("category"),
        tags = getValue("tags").jsonArray.map { it.jsonPrimitive.content },
        updatedAt = getValue("updatedAt").jsonPrimitive.content,
        dataSource = nullableString("dataSource")
    )

    private fun JsonObject.toTags() = ProjectTagsCli(
        projectId = getValue("projectId").jsonPrimitive.content.toInt(),
        tags = getValue("tags").jsonArray.map { it.jsonPrimitive.content }
    )

    private fun JsonObject.nullableString(name: String) = this[name]
        ?.takeUnless { it is JsonNull }
        ?.jsonPrimitive
        ?.content

    private fun executeNoContent(method: String, path: String) {
        val response = transport.execute(config, TaskHttpRequest(method, path))
        if (response.status !in 200..299) {
            throw TaskCliHttpException(response.status, response.body.take(500).ifBlank { "No response body" })
        }
    }

    private fun metadataPath(projectId: Int) = "/api/v1/projects/$projectId/metadata"
    private fun tagsPath(projectId: Int) = "${metadataPath(projectId)}/tags"
    private fun positive(projectId: Int) = require(projectId > 0) { "Project ID must be greater than zero." }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
