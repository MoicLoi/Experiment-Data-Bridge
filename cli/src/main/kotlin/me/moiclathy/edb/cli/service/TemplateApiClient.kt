package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TemplateCliSummary(val id: Long, val name: String, val type: String, val visibility: String)

data class TemplateCli(
    val id: Long,
    val name: String,
    val description: String,
    val type: String,
    val visibility: String,
    val createdAt: String,
    val updatedAt: String,
    val author: String,
    val version: Int,
    val yaml: String
)

class TemplateApiClient(
    private val config: TaskServerConfig,
    private val transport: TaskHttpTransport = JdkTaskHttpTransport()
) {
    private val api = TaskApiClient(config, transport)

    fun list(keyword: String?, type: String?, visibility: String?): List<TemplateCliSummary> {
        val normalizedType = type?.let(::templateType)
        val normalizedVisibility = visibility?.let(::templateVisibility)
        val query = buildList {
            keyword?.takeIf { it.isNotBlank() }?.let { add("keyword=${encode(it)}") }
            normalizedType?.let { add("type=$it") }
            normalizedVisibility?.let { add("visibility=$it") }
        }.joinToString("&")
        val path = TEMPLATES_PATH + if (query.isBlank()) "" else "?$query"
        return api.requestElement("GET", path).jsonArray.map { element ->
            val value = element.jsonObject
            TemplateCliSummary(value.long("id"), value.string("name"), value.string("type"), value.string("visibility"))
        }
    }

    fun info(id: Long): TemplateCli {
        positive(id)
        return api.requestElement("GET", "$TEMPLATES_PATH/$id").jsonObject
            .getValue("template").jsonObject.toTemplate()
    }

    fun create(
        name: String,
        description: String,
        type: String,
        visibility: String,
        yaml: String
    ): TemplateCli {
        validate(name, yaml)
        return api.requestElement("POST", TEMPLATES_PATH, body(
            name, description, templateType(type), templateVisibility(visibility), yaml
        )).jsonObject.getValue("template").jsonObject.toTemplate()
    }

    fun update(
        id: Long,
        yaml: String,
        name: String?,
        description: String?,
        type: String?,
        visibility: String?
    ): TemplateCli {
        positive(id)
        require(yaml.isNotBlank()) { "Template YAML must not be blank." }
        val current = info(id)
        return api.requestElement("PUT", "$TEMPLATES_PATH/$id", body(
            name ?: current.name,
            description ?: current.description,
            type?.let(::templateType) ?: current.type,
            visibility?.let(::templateVisibility) ?: current.visibility,
            yaml
        )).jsonObject.getValue("template").jsonObject.toTemplate()
    }

    fun delete(id: Long) {
        positive(id)
        val response = transport.execute(config, TaskHttpRequest("DELETE", "$TEMPLATES_PATH/$id"))
        if (response.status !in 200..299) {
            throw TaskCliHttpException(response.status, response.body.take(500).ifBlank { "No response body" })
        }
    }

    private fun body(name: String, description: String, type: String, visibility: String, yaml: String) = buildJsonObject {
        put("name", name)
        put("description", description)
        put("type", type)
        put("visibility", visibility)
        put("yaml", yaml)
    }

    private fun JsonObject.toTemplate(): TemplateCli {
        val metadata = getValue("metadata").jsonObject
        return TemplateCli(
            long("id"), string("name"), string("description"), string("type"), string("visibility"),
            metadata.string("createdAt"), metadata.string("updatedAt"), metadata.string("author"),
            metadata.int("version"), string("yaml")
        )
    }

    private fun validate(name: String, yaml: String) {
        require(name.isNotBlank()) { "Template name must not be blank." }
        require(yaml.isNotBlank()) { "Template YAML must not be blank." }
    }

    private fun templateType(value: String): String = value.uppercase().also {
        require(it in TYPES) { "Template type must be EXPERIMENT, SAMPLE, ANIMAL, PROCEDURE, or REPORT." }
    }

    private fun templateVisibility(value: String): String = value.uppercase().also {
        require(it in VISIBILITIES) { "Template visibility must be PRIVATE, PROJECT, or PUBLIC." }
    }

    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.long(name: String) = getValue(name).jsonPrimitive.long
    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int
    private fun positive(id: Long) = require(id > 0) { "Template ID must be greater than zero." }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private companion object {
        const val TEMPLATES_PATH = "/api/v1/templates"
        val TYPES = setOf("EXPERIMENT", "SAMPLE", "ANIMAL", "PROCEDURE", "REPORT")
        val VISIBILITIES = setOf("PRIVATE", "PROJECT", "PUBLIC")
    }
}
