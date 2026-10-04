package me.moiclathy.edb.agent.provider

import java.net.URI
import java.time.Duration

data class AgentModelProfile(
    val id: String,
    val providerId: String,
    val modelName: String,
    val baseUrl: URI? = null,
    val apiKeySecretRef: String? = null,
    val temperature: Double = 0.0,
    val maxOutputTokens: Int = 4_096,
    val timeout: Duration = Duration.ofSeconds(60),
    val toolCallingEnabled: Boolean = true,
    val structuredOutputEnabled: Boolean = true,
    val maxConcurrency: Int = 4,
    val requestsPerMinute: Int = 60,
    val circuitBreakerFailures: Int = 5,
    val circuitBreakerOpenDuration: Duration = Duration.ofSeconds(30)
) {
    init {
        requireToken(id, "Agent model profile id")
        requireToken(providerId, "Agent model provider id")
        requireToken(modelName, "Agent model name")
        require(apiKeySecretRef == null || apiKeySecretRef.isNotBlank()) {
            "Agent model API key secret reference must not be blank."
        }
        require(temperature.isFinite() && temperature in 0.0..2.0) {
            "Agent model temperature must be finite and between 0 and 2."
        }
        require(maxOutputTokens > 0) { "Agent model maximum output tokens must be greater than zero." }
        require(!timeout.isZero && !timeout.isNegative) { "Agent model timeout must be greater than zero." }
        require(baseUrl == null || (baseUrl.isAbsolute && baseUrl.scheme in setOf("http", "https"))) {
            "Agent model base URL must be an absolute HTTP(S) URI."
        }
        require(maxConcurrency > 0) { "Agent model maximum concurrency must be greater than zero." }
        require(requestsPerMinute > 0) { "Agent model requests per minute must be greater than zero." }
        require(circuitBreakerFailures > 0) { "Agent model circuit failure threshold must be greater than zero." }
        require(!circuitBreakerOpenDuration.isZero && !circuitBreakerOpenDuration.isNegative) {
            "Agent model circuit open duration must be greater than zero."
        }
    }
}

private fun requireToken(value: String, label: String) {
    require(value.isNotBlank()) { "$label must not be blank." }
    require(value == value.trim()) { "$label must not contain surrounding whitespace." }
}
