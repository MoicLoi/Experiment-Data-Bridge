package me.moiclathy.edb.agent.policy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.agent.provider.AgentModelResponse
import java.security.MessageDigest

data class AgentPromptInspection(
    val sanitizedText: String,
    val matchedRule: String? = null,
    val fingerprint: String
) {
    val allowed: Boolean get() = matchedRule == null
}

class AgentPromptSecurity(
    private val redactor: AgentSensitiveDataRedactor = AgentSensitiveDataRedactor()
) {
    fun inspect(text: String): AgentPromptInspection {
        val rule = INJECTION_RULES.firstOrNull { it.pattern.containsMatchIn(text) }?.code
        return AgentPromptInspection(redactor.redact(text), rule, text.sha256())
    }

    fun requireSafe(text: String): AgentPromptInspection = inspect(text).also { inspection ->
        if (!inspection.allowed) throw AgentPromptInjectionRejectedException(
            requireNotNull(inspection.matchedRule), inspection.fingerprint, text.length
        )
    }

    private data class Rule(val code: String, val pattern: Regex)

    private companion object {
        val INJECTION_RULES = listOf(
            Rule("OVERRIDE_INSTRUCTIONS", Regex(
                "(?is)\\b(ignore|disregard|override|forget)\\b.{0,80}\\b(previous|prior|system|developer|instructions?)\\b"
            )),
            Rule("SECRET_EXFILTRATION", Regex(
                "(?is)\\b(reveal|show|print|return|expose|dump|send)\\b.{0,100}\\b(api[ _-]?key|access[ _-]?token|password|secret|environment variables?|system prompt)\\b"
            )),
            Rule("AUTHORIZATION_BYPASS", Regex(
                "(?is)\\b(bypass|disable|ignore|circumvent)\\b.{0,100}\\b(permission|authorization|policy|approval|guardrail|tool restriction)\\b"
            )),
            Rule("TOOL_RESULT_FORGERY", Regex(
                "(?is)\\b(pretend|fabricate|forge)\\b.{0,80}\\b(tool result|tool call|approval result)\\b"
            )),
            Rule("EXFILTRATION", Regex("(?is)\\bexfiltrat(e|ion|ing)\\b|\\bsend\\b.{0,100}https?://"))
        )
    }
}

class AgentSensitiveDataRedactor {
    fun redact(value: String): String = PATTERNS.fold(value) { current, pattern ->
        pattern.regex.replace(current, pattern.replacement)
    }

    fun redact(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (SENSITIVE_KEY.matches(key)) JsonPrimitive(REDACTED) else redact(value)
        })
        is JsonArray -> JsonArray(element.map(::redact))
        is JsonPrimitive -> if (element.isString) JsonPrimitive(redact(element.content)) else element
    }

    fun redact(value: JsonObject): JsonObject = redact(value as JsonElement) as JsonObject

    fun redact(response: AgentModelResponse): AgentModelResponse = response.copy(
        text = response.text?.let(::redact),
        structuredOutput = response.structuredOutput?.let(::redact),
        toolCalls = response.toolCalls.map { it.copy(arguments = redact(it.arguments)) }
    )

    private data class Pattern(val regex: Regex, val replacement: String)
    private companion object {
        const val REDACTED = "[REDACTED]"
        val SENSITIVE_KEY = Regex("(?i).*(api.?key|access.?token|authorization|password|passwd|secret|private.?key).*")
        val PATTERNS = listOf(
            Pattern(Regex("(?is)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----.*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"), REDACTED),
            Pattern(Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+\\-/]+=*"), "Bearer $REDACTED"),
            Pattern(Regex("\\bsk-[A-Za-z0-9_-]{12,}\\b"), REDACTED),
            Pattern(
                Regex("(?i)\\b(api[_-]?key|access[_-]?token|password|passwd|secret)\\b\\s*[:=]\\s*[\\\"']?[^\\s\\\"',;}]+"),
                "\$1=$REDACTED"
            )
        )
    }
}

class AgentPromptInjectionRejectedException(
    val rule: String,
    val fingerprint: String,
    val promptLength: Int
) : EDBException("AGENT_PROMPT_INJECTION_REJECTED", "Agent prompt was rejected by security policy: $rule")

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
