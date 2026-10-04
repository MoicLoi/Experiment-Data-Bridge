package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class AgentApiClient(
    private val config: TaskServerConfig,
    private val transport: TaskHttpTransport = JdkTaskHttpTransport()
) {
    fun profiles() = request("GET", "/api/v1/agent-model-profiles")
    fun quota(projectId: Int) = request("GET", "/api/v1/projects/${positive(projectId, "Project")}/agent-token-quota")
    fun providerStatuses() = request("GET", "/api/v1/admin/agent-providers")
    fun modelMetrics() = request("GET", "/api/v1/admin/agent-model-metrics")
    fun metrics() = request("GET", "/api/v1/admin/agent-metrics")
    fun retentionOverview() = request("GET", AGENT_RETENTION_PATH)
    fun retentionEstimate() = request("GET", "$AGENT_RETENTION_PATH/estimate")
    fun retentionCleanup() = request("POST", "$AGENT_RETENTION_PATH/cleanup")
    fun retentionLastResult() = request("GET", "$AGENT_RETENTION_PATH/last-result", allowNoContent = true)

    fun createSession(projectId: Int, agentId: String, agentVersion: String): JsonElement {
        require(agentId.isNotBlank()) { "Agent ID must not be blank." }
        require(agentVersion.isNotBlank()) { "Agent version must not be blank." }
        return request("POST", "/api/v1/projects/${positive(projectId, "Project")}/agent-sessions", buildJsonObject {
            put("agentId", agentId); put("agentVersion", agentVersion)
        })
    }

    fun session(sessionId: String) = request("GET", "/api/v1/agent-sessions/${id(sessionId, "Session")}")
    fun closeSession(sessionId: String) = request("POST", "/api/v1/agent-sessions/${id(sessionId, "Session")}/close")
    fun messages(sessionId: String) = request("GET", "/api/v1/agent-sessions/${id(sessionId, "Session")}/messages")
    fun send(sessionId: String, message: String, maxRepairAttempts: Int): JsonElement {
        require(message.isNotBlank()) { "Planning message must not be blank." }
        require(maxRepairAttempts in 0..10) { "Maximum repair attempts must be between 0 and 10." }
        return request("POST", "/api/v1/agent-sessions/${id(sessionId, "Session")}/messages", buildJsonObject {
            put("message", message); put("maxRepairAttempts", maxRepairAttempts)
        })
    }

    fun run(runId: String) = request("GET", "/api/v1/agent-runs/${id(runId, "Run")}")
    fun cancelRun(runId: String) = request("POST", "/api/v1/agent-runs/${id(runId, "Run")}/cancel")
    fun timeline(runId: String) = request("GET", "/api/v1/agent-runs/${id(runId, "Run")}/audit")
    fun proposals(runId: String) = request("GET", "/api/v1/agent-runs/${id(runId, "Run")}/proposals")

    fun proposal(proposalId: String) = request("GET", "/api/v1/agent-proposals/${id(proposalId, "Proposal")}")
    fun approve(proposalId: String) = request("POST", "/api/v1/agent-proposals/${id(proposalId, "Proposal")}/approve")
    fun reject(proposalId: String, reason: String): JsonElement {
        require(reason.isNotBlank()) { "Rejection reason must not be blank." }
        return request("POST", "/api/v1/agent-proposals/${id(proposalId, "Proposal")}/reject", buildJsonObject { put("reason", reason) })
    }
    fun decision(proposalId: String) = request("GET", "/api/v1/agent-proposals/${id(proposalId, "Proposal")}/decision")
    fun submit(proposalId: String, context: Map<String, String>) = request(
        "POST", "/api/v1/agent-proposals/${id(proposalId, "Proposal")}/submit",
        buildJsonObject { putJsonObject("context") { context.forEach { (key, value) -> put(key, value) } } }
    )

    private fun request(method: String, path: String, body: JsonObject? = null, allowNoContent: Boolean = false): JsonElement {
        val response = transport.execute(config, TaskHttpRequest(method, path, body?.toString()))
        if (response.status !in 200..299) throw TaskCliHttpException(response.status, response.body.take(1_000).ifBlank { "No response body" })
        if (response.status == 204 && allowNoContent) return JsonNull
        return runCatching { Json.parseToJsonElement(response.body) }
            .getOrElse { throw TaskCliConnectionException("Server returned invalid Agent JSON: ${it.message}", it) }
    }

    private fun positive(value: Int, label: String) = value.also { require(it > 0) { "$label ID must be greater than zero." } }
    private fun id(value: String, label: String) = value.trim().also { require(it.isNotEmpty()) { "$label ID must not be blank." } }

    private companion object { const val AGENT_RETENTION_PATH = "/api/v1/admin/agent-retention" }
}

data class AgentLifecycleCliEvent(
    val eventId: String?, val sessionId: String, val runId: String?, val projectId: Int,
    val type: String, val runStatus: String?, val detailsJson: String, val occurredAt: String
) {
    val terminal: Boolean get() = runStatus in setOf("COMPLETED", "FAILED", "CANCELLED")
}

enum class AgentWatchStopReason { TERMINAL, TIMEOUT, ALREADY_TERMINAL }
