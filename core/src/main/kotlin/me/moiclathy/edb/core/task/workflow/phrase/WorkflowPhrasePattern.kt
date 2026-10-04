package me.moiclathy.edb.core.task.workflow.phrase

import java.util.concurrent.ConcurrentHashMap

enum class WorkflowPhraseTokenType { LITERAL, ENUM, PARAMETER }

data class WorkflowPhraseToken(
    val type: WorkflowPhraseTokenType,
    val name: String? = null,
    val i18nKey: String? = null,
    val source: String? = null,
    val optional: Boolean = false
)

data class WorkflowPhrasePattern(
    val id: String,
    val stepType: String,
    val stepVersion: String = "1",
    val tokens: List<WorkflowPhraseToken>
)

class WorkflowPhrasePatternRegistry(patterns: Iterable<WorkflowPhrasePattern> = emptyList()) {
    private val values = ConcurrentHashMap<String, WorkflowPhrasePattern>()

    init { patterns.forEach(::register) }

    fun register(pattern: WorkflowPhrasePattern) {
        require(ID.matches(pattern.id)) { "Phrase pattern id is invalid: ${pattern.id}" }
        require(pattern.stepType.isNotBlank()) { "Phrase pattern step type must not be blank." }
        require(pattern.stepVersion.isNotBlank()) { "Phrase pattern step version must not be blank." }
        require(pattern.tokens.isNotEmpty()) { "Phrase pattern must contain at least one token." }
        pattern.tokens.forEach { token ->
            when (token.type) {
                WorkflowPhraseTokenType.LITERAL -> require(!token.i18nKey.isNullOrBlank()) { "Literal phrase token requires i18nKey." }
                WorkflowPhraseTokenType.ENUM -> require(!token.name.isNullOrBlank() && !token.source.isNullOrBlank()) { "Enum phrase token requires name and source." }
                WorkflowPhraseTokenType.PARAMETER -> require(!token.name.isNullOrBlank()) { "Parameter phrase token requires name." }
            }
        }
        val key = key(pattern.stepType, pattern.stepVersion, pattern.id)
        require(values.putIfAbsent(key, pattern) == null) { "Phrase pattern already registered: $key" }
    }

    fun find(stepType: String, stepVersion: String = "1"): List<WorkflowPhrasePattern> = values.values
        .filter { it.stepType == stepType && it.stepVersion == stepVersion }
        .sortedBy { it.id }

    companion object {
        private val ID = Regex("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$")
        private fun key(type: String, version: String, id: String) = "$type@$version#$id"

        fun builtIns() = WorkflowPhrasePatternRegistry(listOf(
            WorkflowPhrasePattern("convert-short", "datafile.convert", tokens = listOf(
                WorkflowPhraseToken(WorkflowPhraseTokenType.LITERAL, i18nKey = "phrase.execute"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.LITERAL, i18nKey = "phrase.convert"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.ENUM, name = "targetFormat", source = "conversion.formats")
            )),
            WorkflowPhrasePattern("convert-natural", "datafile.convert", tokens = listOf(
                WorkflowPhraseToken(WorkflowPhraseTokenType.LITERAL, i18nKey = "phrase.convert"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.ENUM, name = "targetFormat", source = "conversion.formats")
            )),
            WorkflowPhrasePattern("analysis-short", "datafile.analysis.run", tokens = listOf(
                WorkflowPhraseToken(WorkflowPhraseTokenType.LITERAL, i18nKey = "phrase.execute"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.LITERAL, i18nKey = "phrase.analysis"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.ENUM, name = "handlerId", source = "analysis.handlers")
            )),
            WorkflowPhrasePattern("report-short", "datafile.report.generate", tokens = listOf(
                WorkflowPhraseToken(WorkflowPhraseTokenType.LITERAL, i18nKey = "phrase.generate"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.LITERAL, i18nKey = "phrase.report"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.PARAMETER, name = "definitionId")
            )),
            WorkflowPhrasePattern("step-short", "context.set", tokens = listOf(
                WorkflowPhraseToken(WorkflowPhraseTokenType.LITERAL, i18nKey = "phrase.execute"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.PARAMETER, name = "key"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.PARAMETER, name = "value")
            )),
            WorkflowPhrasePattern("copy-short", "context.copy", tokens = listOf(
                WorkflowPhraseToken(WorkflowPhraseTokenType.LITERAL, i18nKey = "phrase.copy"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.PARAMETER, name = "from"),
                WorkflowPhraseToken(WorkflowPhraseTokenType.PARAMETER, name = "to")
            ))
        ))
    }
}
