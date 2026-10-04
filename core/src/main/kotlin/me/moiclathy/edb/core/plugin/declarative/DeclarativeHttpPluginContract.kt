package me.moiclathy.edb.core.plugin.declarative

import kotlinx.serialization.Serializable

@Serializable
data class DeclarativeHttpPluginDocument(val schemaVersion: Int, val plugin: DeclarativePluginIdentity, val connection: DeclarativeHttpConnection, val capabilities: List<DeclarativeHttpCapability>)

@Serializable
data class DeclarativePluginIdentity(val id: String, val version: String, val adapter: String = ADAPTER_ID) {
    companion object { const val ADAPTER_ID = "edb-http-adapter" }
}

@Serializable
data class DeclarativeHttpConnection(val baseUrl: String, val healthPath: String = "/health", val auth: DeclarativeHttpAuth = DeclarativeHttpAuth())

@Serializable
data class DeclarativeHttpAuth(val type: DeclarativeHttpAuthType = DeclarativeHttpAuthType.NONE, val secretRef: String? = null)

@Serializable enum class DeclarativeHttpAuthType { NONE, BEARER }

@Serializable
data class DeclarativeHttpCapability(
    val id: String,
    val type: DeclarativeCapabilityType,
    val titleKey: String,
    val descriptionKey: String? = null,
    val request: DeclarativeHttpRequest,
    val inputFormats: List<String>,
    val outputFormat: String,
    val parameterSchema: String,
    val workflow: DeclarativeWorkflowMapping = DeclarativeWorkflowMapping(),
    val cli: DeclarativeCliMapping? = null,
    val phrasePatterns: List<DeclarativePhrasePattern> = emptyList()
)

@Serializable enum class DeclarativeCapabilityType { ANALYSIS }
@Serializable data class DeclarativeHttpRequest(val method: String = "POST", val path: String)
@Serializable data class DeclarativeWorkflowMapping(val stepType: String = "datafile.analysis.run", val stepVersion: Int = 1)
@Serializable data class DeclarativeCliMapping(val path: List<String>)
@Serializable data class DeclarativePhrasePattern(val id:String,val tokens:List<DeclarativePhraseToken>)
@Serializable data class DeclarativePhraseToken(val type:DeclarativePhraseTokenType,val name:String?=null,val i18nKey:String?=null,val source:String?=null,val optional:Boolean=false)
@Serializable enum class DeclarativePhraseTokenType { LITERAL, ENUM, PARAMETER }
