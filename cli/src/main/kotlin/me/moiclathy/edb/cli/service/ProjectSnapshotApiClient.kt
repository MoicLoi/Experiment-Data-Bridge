package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

data class ProjectSnapshotCli(
    val id: Long,
    val projectId: Int,
    val version: Int,
    val createdAt: String,
    val createdBy: String,
    val description: String?,
    val yaml: String
)

data class SnapshotChangeCli(
    val path: String,
    val type: String,
    val sourceValue: JsonElement?,
    val targetValue: JsonElement?
)

data class SnapshotCompareCli(
    val sourceSnapshotId: Long,
    val targetSnapshotId: Long,
    val sourceVersion: Int,
    val targetVersion: Int,
    val identical: Boolean,
    val changes: List<SnapshotChangeCli>
)

data class SnapshotMergeConflictCli(
    val path: String,
    val baseValue: String?,
    val sourceValue: String?,
    val targetValue: String?,
    val reason: String
)

data class SnapshotMergeCli(
    val targetSnapshotId: Long,
    val sourceSnapshotId: Long,
    val mergedYaml: String?,
    val conflicts: List<SnapshotMergeConflictCli>
) {
    val success: Boolean get() = conflicts.isEmpty() && mergedYaml != null
}

class ProjectSnapshotApiClient(
    private val config: TaskServerConfig,
    private val transport: TaskHttpTransport = JdkTaskHttpTransport()
) {
    private val api = TaskApiClient(config, transport)
    private val projects = ProjectApiClient(config, transport)

    fun create(projectId: Int, description: String?): ProjectSnapshotCli {
        positiveProject(projectId)
        val yaml = projects.get(projectId).configYaml
            ?: throw IllegalStateException("Project #$projectId configuration is not initialized.")
        return api.requestElement("POST", snapshotsPath(projectId), buildJsonObject {
            put("yaml", yaml)
            description?.let { put("description", it) }
        }).jsonObject.toSnapshot()
    }

    fun list(projectId: Int): List<ProjectSnapshotCli> {
        positiveProject(projectId)
        return api.requestElement("GET", snapshotsPath(projectId)).jsonArray.map { it.jsonObject.toSnapshot() }
    }

    fun latest(projectId: Int): ProjectSnapshotCli {
        positiveProject(projectId)
        return api.requestElement("GET", "${snapshotsPath(projectId)}/latest").jsonObject.toSnapshot()
    }

    fun info(snapshotId: Long): ProjectSnapshotCli {
        positiveSnapshot(snapshotId)
        return api.requestElement("GET", snapshotPath(snapshotId)).jsonObject.toSnapshot()
    }

    fun delete(snapshotId: Long) {
        positiveSnapshot(snapshotId)
        executeNoContent("DELETE", snapshotPath(snapshotId))
    }

    fun compare(projectId: Int, sourceId: Long, targetId: Long): SnapshotCompareCli {
        positiveProject(projectId)
        positiveSnapshot(sourceId)
        positiveSnapshot(targetId)
        require(sourceId != targetId) { "Source and target snapshots must be different." }
        val result = api.requestElement(
            "GET", "${snapshotsPath(projectId)}/compare?sourceSnapshotId=$sourceId&targetSnapshotId=$targetId"
        ).jsonObject
        return SnapshotCompareCli(
            result.long("sourceSnapshotId"),
            result.long("targetSnapshotId"),
            result.int("sourceVersion"),
            result.int("targetVersion"),
            result.getValue("identical").jsonPrimitive.boolean,
            result.getValue("changes").jsonArray.map { element ->
                val change = element.jsonObject
                SnapshotChangeCli(
                    change.string("path"), change.string("type"),
                    change["sourceValue"]?.takeUnless { it is JsonNull },
                    change["targetValue"]?.takeUnless { it is JsonNull }
                )
            }
        )
    }

    fun merge(projectId: Int, sourceId: Long, targetId: Long, strategy: String): SnapshotMergeCli {
        positiveProject(projectId)
        positiveSnapshot(sourceId)
        positiveSnapshot(targetId)
        require(sourceId != targetId) { "Source and target snapshots must be different." }
        val normalizedStrategy = strategy.uppercase()
        require(normalizedStrategy in MERGE_STRATEGIES) {
            "Merge strategy must be RECURSIVE, SOURCE_WINS, TARGET_WINS, or FAIL_ON_CONFLICT."
        }
        val response = transport.execute(config, TaskHttpRequest(
            "POST", "/api/v1/projects/$projectId/config/merge",
            buildJsonObject {
                put("targetSnapshotId", targetId)
                put("sourceSnapshotId", sourceId)
                put("strategy", normalizedStrategy)
            }.toString()
        ))
        if (response.status !in 200..299 && response.status != 409) {
            throw TaskCliHttpException(response.status, response.body.take(500).ifBlank { "No response body" })
        }
        val result = Json.parseToJsonElement(response.body).jsonObject
        val conflicts = result["conflicts"]?.jsonArray?.map { element ->
            val conflict = element.jsonObject
            SnapshotMergeConflictCli(
                conflict.string("path"), conflict.nullableString("baseValue"),
                conflict.nullableString("sourceValue"), conflict.nullableString("targetValue"),
                conflict.string("reason")
            )
        }.orEmpty()
        return SnapshotMergeCli(
            result.long("targetSnapshotId"), result.long("sourceSnapshotId"),
            result.nullableString("mergedYaml"), conflicts
        )
    }

    private fun JsonObject.toSnapshot(): ProjectSnapshotCli {
        val metadata = getValue("metadata").jsonObject
        return ProjectSnapshotCli(
            getValue("id").jsonPrimitive.long,
            getValue("projectId").jsonPrimitive.int,
            getValue("version").jsonPrimitive.int,
            metadata.string("createdAt"), metadata.string("createdBy"),
            metadata.nullableString("description"), string("yaml")
        )
    }

    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int
    private fun JsonObject.long(name: String) = getValue(name).jsonPrimitive.long
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

    private fun snapshotsPath(projectId: Int) = "/api/v1/projects/$projectId/config/snapshots"
    private fun snapshotPath(snapshotId: Long) = "/api/v1/config/snapshots/$snapshotId"
    private fun positiveProject(id: Int) = require(id > 0) { "Project ID must be greater than zero." }
    private fun positiveSnapshot(id: Long) = require(id > 0) { "Snapshot ID must be greater than zero." }

    private companion object {
        val MERGE_STRATEGIES = setOf("RECURSIVE", "SOURCE_WINS", "TARGET_WINS", "FAIL_ON_CONFLICT")
    }
}
