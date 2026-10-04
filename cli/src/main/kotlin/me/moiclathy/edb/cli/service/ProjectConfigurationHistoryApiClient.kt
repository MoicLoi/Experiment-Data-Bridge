package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ProjectConfigurationRevisionCli(
    val commitId: String,
    val shortCommitId: String,
    val message: String,
    val author: String,
    val email: String,
    val time: String
)

data class ProjectConfigurationDiffCli(
    val oldCommit: String,
    val newCommit: String,
    val oldContent: String,
    val newContent: String
)

class ProjectConfigurationHistoryApiClient(
    config: TaskServerConfig,
    transport: TaskHttpTransport = JdkTaskHttpTransport()
) {
    private val api = TaskApiClient(config, transport)

    fun history(projectId: Int): List<ProjectConfigurationRevisionCli> {
        positive(projectId)
        return api.requestElement("GET", "$pathPrefix/$projectId/git/history")
            .jsonArray
            .map { it.jsonObject.toRevision() }
    }

    fun diff(projectId: Int, from: String, to: String): ProjectConfigurationDiffCli {
        positive(projectId)
        require(from.isNotBlank()) { "Source commit must not be blank." }
        require(to.isNotBlank()) { "Target commit must not be blank." }
        val result = api.requestElement(
            "GET",
            "$pathPrefix/$projectId/git/diff?from=${encode(from)}&to=${encode(to)}"
        ).jsonObject
        return ProjectConfigurationDiffCli(
            oldCommit = result.string("oldCommit"),
            newCommit = result.string("newCommit"),
            oldContent = result.string("oldContent"),
            newContent = result.string("newContent")
        )
    }

    fun restore(projectId: Int, commitId: String): ProjectConfigurationRevisionCli {
        positive(projectId)
        require(commitId.isNotBlank()) { "Commit ID must not be blank." }
        return api.requestElement(
            "POST",
            "$pathPrefix/$projectId/git/restore",
            buildJsonObject { put("commitId", commitId) }
        ).jsonObject.toRevision()
    }

    private fun JsonObject.toRevision() = ProjectConfigurationRevisionCli(
        commitId = string("commitId"),
        shortCommitId = string("shortCommitId"),
        message = string("message"),
        author = string("author"),
        email = string("email"),
        time = string("time")
    )

    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun positive(projectId: Int) = require(projectId > 0) { "Project ID must be greater than zero." }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private companion object {
        const val pathPrefix = "/api/v1/projects"
    }
}
