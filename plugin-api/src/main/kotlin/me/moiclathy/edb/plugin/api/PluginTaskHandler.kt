package me.moiclathy.edb.plugin.api

interface PluginTaskHandler {
    val taskType: String

    suspend fun execute(
        request: PluginTaskRequest,
        context: PluginExecutionContext
    ): PluginTaskResult?
}

data class PluginTaskRequest(
    val taskId: Int?,
    val projectId: Int,
    val triggerUserId: Int,
    val attemptCount: Int,
    val configSnapshot: String?
)

interface PluginExecutionContext {
    suspend fun ensureNotCancelled()

    suspend fun reportProgress(percent: Int, message: String? = null)
}

data class PluginTaskResult(
    val outputPath: String? = null,
    val contentType: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
