package me.moiclathy.edb.core.plugin.declarative

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandler
import me.moiclathy.edb.core.datafile.analysis.AnalysisParameterContract
import me.moiclathy.edb.core.datafile.analysis.AnalysisCapabilityMetadata
import me.moiclathy.edb.core.datafile.analysis.ExternalHttpAnalysisHandlerConfig
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhrasePattern
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhraseToken
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhraseTokenType

data class LoadedDeclarativeHttpPlugin(val descriptor: Path, val document: DeclarativeHttpPluginDocument, val handlers: List<AnalysisHandler>)
data class DeclarativeHttpPluginLoadFailure(val descriptor: Path, val message: String, val cause: Throwable)
data class DeclarativeHttpPluginLoadResult(val directory: Path, val loaded: List<LoadedDeclarativeHttpPlugin>, val failures: List<DeclarativeHttpPluginLoadFailure>) {
    val handlers: List<AnalysisHandler> get() = loaded.flatMap { it.handlers }
}

class DeclarativeHttpPluginLoader(
    allowedHosts: Set<String>,
    private val environment: (String) -> String? = System::getenv,
    private val json: Json = Json { ignoreUnknownKeys = false }
) {
    private val allowedHosts = allowedHosts.map(String::lowercase).toSet()

    init { require(this.allowedHosts.isNotEmpty()) { "At least one declarative plugin host must be allowed." } }

    fun load(directory: Path): DeclarativeHttpPluginLoadResult {
        val root = directory.toAbsolutePath().normalize()
        if (!Files.exists(root)) return DeclarativeHttpPluginLoadResult(root, emptyList(), emptyList())
        require(Files.isDirectory(root)) { "Declarative plugin path is not a directory: $root" }
        val descriptors = Files.walk(root, 4).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString() == "plugin.json" }.sorted().toList()
        }
        val loaded = mutableListOf<LoadedDeclarativeHttpPlugin>()
        val failures = mutableListOf<DeclarativeHttpPluginLoadFailure>()
        descriptors.forEach { descriptor ->
            runCatching { loadOne(root, descriptor) }.onSuccess(loaded::add).onFailure {
                failures += DeclarativeHttpPluginLoadFailure(descriptor, it.message ?: "Unknown load failure", it)
            }
        }
        val duplicates = loaded.flatMap { plugin -> plugin.handlers.map { it.id } }.groupingBy { it }.eachCount().filterValues { it > 1 }
        require(duplicates.isEmpty()) { "Duplicate declarative capability ids: ${duplicates.keys.sorted().joinToString()}" }
        return DeclarativeHttpPluginLoadResult(root, loaded, failures)
    }

    private fun loadOne(root: Path, descriptor: Path): LoadedDeclarativeHttpPlugin {
        val document = json.decodeFromString<DeclarativeHttpPluginDocument>(Files.readString(descriptor))
        validateDocument(document)
        val base = URI(document.connection.baseUrl)
        validateEndpoint(base)
        val token = resolveAuth(document.connection.auth)
        val handlers = document.capabilities.map { capability ->
            validateCapability(capability)
            val endpoint = base.resolve(capability.request.path)
            require(endpoint.scheme == base.scheme && endpoint.host == base.host && endpoint.port == base.port) {
                "Capability '${capability.id}' endpoint must remain on the configured origin."
            }
            val schemaPath = descriptor.parent.resolve(capability.parameterSchema).normalize()
            require(schemaPath.startsWith(descriptor.parent.normalize()) && schemaPath.startsWith(root)) {
                "Capability '${capability.id}' parameterSchema escapes its plugin directory."
            }
            val schema = json.parseToJsonElement(Files.readString(schemaPath)).jsonObject
            DeclarativeHttpAnalysisHandler(
                ExternalHttpAnalysisHandlerConfig(
                    id = capability.id,
                    analysisType = capability.id,
                    tool = document.plugin.id,
                    endpoint = endpoint,
                    outputFormat = DataFileFormat.valueOf(capability.outputFormat.uppercase()),
                    bearerToken = token
                ),
                AnalysisParameterContract(capability.id, 1, capability.titleKey, capability.descriptionKey, schema),
                AnalysisCapabilityMetadata(
                    sourceType = "DECLARATIVE_HTTP_PLUGIN",
                    sourceId = document.plugin.id,
                    sourceVersion = document.plugin.version,
                    titleKey = capability.titleKey,
                    descriptionKey = capability.descriptionKey,
                    cliPath = capability.cli?.path.orEmpty(),
                    phrasePatterns = capability.phrasePatterns.map { pattern ->
                        WorkflowPhrasePattern(pattern.id, capability.workflow.stepType, capability.workflow.stepVersion.toString(), pattern.tokens.map { token ->
                            WorkflowPhraseToken(WorkflowPhraseTokenType.valueOf(token.type.name), token.name, token.i18nKey, token.source, token.optional)
                        })
                    }
                ),
                json
            )
        }
        return LoadedDeclarativeHttpPlugin(descriptor, document, handlers)
    }

    private fun validateDocument(document: DeclarativeHttpPluginDocument) {
        require(document.schemaVersion == 1) { "Unsupported declarative plugin schemaVersion: ${document.schemaVersion}" }
        require(document.plugin.adapter == DeclarativePluginIdentity.ADAPTER_ID) { "Unsupported plugin adapter: ${document.plugin.adapter}" }
        require(ID.matches(document.plugin.id)) { "Invalid plugin id: ${document.plugin.id}" }
        require(SEMVER.matches(document.plugin.version)) { "Invalid plugin version: ${document.plugin.version}" }
        require(document.capabilities.isNotEmpty()) { "Declarative plugin must expose at least one capability." }
        require(document.capabilities.map { it.id }.distinct().size == document.capabilities.size) { "Capability ids must be unique." }
    }

    private fun validateEndpoint(uri: URI) {
        require(uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null) { "baseUrl must be an HTTP(S) origin without user info." }
        require(uri.host.lowercase() in allowedHosts) { "Plugin host is not allowed: ${uri.host}" }
    }

    private fun validateCapability(capability: DeclarativeHttpCapability) {
        require(ID.matches(capability.id)) { "Invalid capability id: ${capability.id}" }
        require(TRANSLATION_KEY.matches(capability.titleKey)) { "Invalid titleKey for capability '${capability.id}'." }
        require(capability.request.method.uppercase() == "POST") { "Only POST capabilities are supported." }
        require(capability.request.path.startsWith('/') && ".." !in capability.request.path && '?' !in capability.request.path && '#' !in capability.request.path) { "Unsafe request path." }
        require(capability.inputFormats.isNotEmpty()) { "Capability '${capability.id}' must declare inputFormats." }
        capability.inputFormats.forEach { DataFileFormat.valueOf(it.uppercase()) }
        require(DataFileFormat.valueOf(capability.outputFormat.uppercase()) != DataFileFormat.UNKNOWN) { "Unknown output format." }
        require(capability.workflow.stepType == "datafile.analysis.run" && capability.workflow.stepVersion == 1) { "Capability must map to datafile.analysis.run@1." }
        capability.cli?.path?.let { require(it.isNotEmpty() && it.all(CLI_TOKEN::matches)) { "Invalid CLI path." } }
        require(capability.phrasePatterns.map { it.id }.distinct().size == capability.phrasePatterns.size) { "Duplicate phrase pattern id for capability '${capability.id}'." }
        capability.phrasePatterns.forEach { pattern ->
            require(PHRASE_PATTERN_ID.matches(pattern.id) && pattern.tokens.isNotEmpty()) { "Invalid phrase pattern for capability '${capability.id}'." }
            pattern.tokens.forEach { token -> when(token.type) {
                DeclarativePhraseTokenType.LITERAL -> require(!token.i18nKey.isNullOrBlank()) { "Literal phrase token requires i18nKey." }
                DeclarativePhraseTokenType.ENUM -> require(!token.name.isNullOrBlank() && !token.source.isNullOrBlank()) { "Enum phrase token requires name and source." }
                DeclarativePhraseTokenType.PARAMETER -> require(!token.name.isNullOrBlank()) { "Parameter phrase token requires name." }
            }}
        }
    }

    private fun resolveAuth(auth: DeclarativeHttpAuth): String? = when (auth.type) {
        DeclarativeHttpAuthType.NONE -> { require(auth.secretRef == null) { "NONE authentication must not declare secretRef." }; null }
        DeclarativeHttpAuthType.BEARER -> {
            val ref = requireNotNull(auth.secretRef) { "BEARER authentication requires secretRef." }
            require(SECRET_REF.matches(ref)) { "Only environment variable secretRef values are supported." }
            environment(ref.removePrefix("env:"))?.takeIf(String::isNotBlank) ?: error("Required plugin secret is unavailable: $ref")
        }
    }

    companion object {
        private val ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")
        private val SEMVER = Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$")
        private val TRANSLATION_KEY = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$")
        private val CLI_TOKEN = Regex("^[a-z][a-z0-9-]{0,49}$")
        private val SECRET_REF = Regex("^env:[A-Z][A-Z0-9_]{1,99}$")
        private val PHRASE_PATTERN_ID = Regex("^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$")
    }
}
