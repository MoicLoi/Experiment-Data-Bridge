package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Path

data class ConfigurationValidationErrorCli(val field: String, val message: String)
data class ConfigurationValidationCliResult(
    val valid: Boolean,
    val errors: List<ConfigurationValidationErrorCli>
)

enum class ProjectConfigurationFileFormat { YAML, EDB }

class ProjectConfigurationApiClient(
    private val config: TaskServerConfig,
    jsonTransport: TaskHttpTransport = JdkTaskHttpTransport(),
    private val binaryTransport: ProjectConfigurationBinaryTransport = JdkProjectConfigurationBinaryTransport()
) {
    private val api = TaskApiClient(config, jsonTransport)
    private val projects = ProjectApiClient(config, jsonTransport)

    fun show(projectId: Int): String {
        positive(projectId)
        return projects.get(projectId).configYaml
            ?: throw IllegalStateException("Project #$projectId configuration is not initialized.")
    }

    fun update(projectId: Int, yaml: String): JsonObject = mutation(projectId, "PUT", "", yaml)

    fun validate(projectId: Int, yaml: String): ConfigurationValidationCliResult {
        positive(projectId)
        require(yaml.isNotBlank()) { "Configuration YAML must not be blank." }
        val json = api.requestElement(
            "POST",
            "${configurationPath(projectId)}/validate",
            buildJsonObject { put("yaml", yaml) }
        ).jsonObject
        return ConfigurationValidationCliResult(
            valid = json.getValue("valid").jsonPrimitive.boolean,
            errors = json.getValue("errors").jsonArray.map { element ->
                val error = element.jsonObject
                ConfigurationValidationErrorCli(
                    error.getValue("field").jsonPrimitive.content,
                    error.getValue("message").jsonPrimitive.content
                )
            }
        )
    }

    fun importYaml(projectId: Int, yaml: String): JsonObject = mutation(projectId, "POST", "/import", yaml)

    fun export(
        projectId: Int,
        format: ProjectConfigurationFileFormat,
        target: Path,
        force: Boolean
    ): ProjectConfigurationDownloadResult {
        positive(projectId)
        val path = when (format) {
            ProjectConfigurationFileFormat.YAML -> "${configurationPath(projectId)}/export"
            ProjectConfigurationFileFormat.EDB -> "/api/v1/projects/$projectId/package"
        }
        return binaryTransport.download(config, path, target, force)
    }

    fun importPackage(projectId: Int, file: Path): JsonObject {
        positive(projectId)
        val response = binaryTransport.uploadPackage(config, projectId, file)
        if (response.status !in 200..299) {
            throw TaskCliHttpException(response.status, response.body.take(500).ifBlank { "No response body" })
        }
        return kotlinx.serialization.json.Json.parseToJsonElement(response.body).jsonObject
    }

    private fun mutation(projectId: Int, method: String, suffix: String, yaml: String): JsonObject {
        positive(projectId)
        require(yaml.isNotBlank()) { "Configuration YAML must not be blank." }
        return api.requestElement(
            method,
            configurationPath(projectId) + suffix,
            buildJsonObject { put("yaml", yaml) }
        ).jsonObject
    }

    private fun configurationPath(projectId: Int) = "/api/v1/projects/$projectId/configuration"
    private fun positive(projectId: Int) = require(projectId > 0) { "Project ID must be greater than zero." }
}
