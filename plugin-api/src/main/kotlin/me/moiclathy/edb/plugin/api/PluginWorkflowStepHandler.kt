package me.moiclathy.edb.plugin.api

interface PluginWorkflowStepHandler {
    val type: String
    val phrasePatterns: List<PluginPhrasePattern> get() = emptyList()

    suspend fun execute(request: PluginWorkflowStepRequest): PluginWorkflowStepResult
}

enum class PluginPhraseTokenType { LITERAL, ENUM, PARAMETER }

data class PluginPhraseToken(
    val type: PluginPhraseTokenType,
    val name: String? = null,
    val i18nKey: String? = null,
    val source: String? = null,
    val optional: Boolean = false
)

data class PluginPhrasePattern(
    val id: String,
    val version: String = "1",
    val tokens: List<PluginPhraseToken>
)

data class PluginWorkflowStepRequest(
    val stepId: String,
    val config: Map<String, String>,
    val context: Map<String, String>
)

sealed interface PluginWorkflowStepResult {
    data class Success(
        val values: Map<String, String> = emptyMap()
    ) : PluginWorkflowStepResult

    data class Failure(
        val code: String,
        val message: String,
        val retryable: Boolean = false
    ) : PluginWorkflowStepResult {
        init {
            require(code.isNotBlank()) { "Plugin workflow failure code must not be blank." }
            require(message.isNotBlank()) { "Plugin workflow failure message must not be blank." }
        }
    }
}
