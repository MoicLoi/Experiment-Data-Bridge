package me.moiclathy.edb.core.plugin

import me.moiclathy.edb.plugin.api.EdbPlugin
import me.moiclathy.edb.plugin.api.EdbPluginApi
import me.moiclathy.edb.plugin.api.PluginContext
import me.moiclathy.edb.plugin.api.PluginContributions
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceConfigurationError
import java.util.ServiceLoader

class DirectoryPluginLoader(
    private val hostApiVersion: String = EdbPluginApi.VERSION,
    private val context: PluginContext = PluginContext()
) {
    init {
        parseApiVersion(hostApiVersion)
    }

    fun load(directory: Path): PluginLoadResult {
        val normalized = directory.toAbsolutePath().normalize()
        if (!Files.exists(normalized)) return PluginLoadResult(normalized, emptyList(), emptyList())
        require(Files.isDirectory(normalized)) { "Plugin path is not a directory: $normalized" }

        val jars = Files.list(normalized).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar", ignoreCase = true) }
                .sorted()
                .toList()
        }
        return loadJars(normalized, jars)
    }

    fun loadJar(jar: Path): PluginLoadResult {
        val normalized = jar.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalized)) { "Plugin JAR does not exist: $normalized" }
        require(normalized.fileName.toString().endsWith(".jar", ignoreCase = true)) {
            "Plugin file must use the .jar extension: $normalized"
        }
        return loadJars(normalized.parent, listOf(normalized))
    }

    private fun loadJars(directory: Path, jars: List<Path>): PluginLoadResult {
        val loaded = mutableListOf<LoadedPlugin>()
        val failures = mutableListOf<PluginLoadFailure>()
        val pluginIds = mutableSetOf<String>()
        val taskTypes = mutableSetOf<String>()
        val workflowTypes = mutableSetOf<String>()

        jars.forEach { jar ->
            when (val candidate = loadCandidate(jar)) {
                is CandidateResult.Failure -> failures += candidate.failure
                is CandidateResult.Success -> {
                    val plugin = candidate.plugin
                    val descriptor = candidate.descriptor
                    val contributions = candidate.contributions
                    val conflict = duplicateFailure(
                        jar,
                        descriptor.id,
                        contributions,
                        pluginIds,
                        taskTypes,
                        workflowTypes
                    )
                    if (conflict != null) {
                        failures += conflict
                        closeCandidate(plugin, candidate.classLoader)
                    } else {
                        try {
                            plugin.initialize(context)
                            pluginIds += descriptor.id
                            taskTypes += contributions.taskHandlers.map { it.taskType }
                            workflowTypes += contributions.workflowStepHandlers.map { it.type }
                            loaded += LoadedPlugin(jar, plugin, descriptor, contributions, candidate.classLoader)
                        } catch (failure: Throwable) {
                            failures += PluginLoadFailure(
                                jar,
                                PluginLoadFailureCode.INITIALIZATION_FAILED,
                                "Plugin '${descriptor.id}' initialization failed: ${failure.message}",
                                failure
                            )
                            closeCandidate(plugin, candidate.classLoader)
                        }
                    }
                }
            }
        }
        return PluginLoadResult(directory, loaded, failures)
    }

    private fun loadCandidate(jar: Path): CandidateResult {
        val classLoader = URLClassLoader(arrayOf(jar.toUri().toURL()), EdbPlugin::class.java.classLoader)
        return try {
            val providers = ServiceLoader.load(EdbPlugin::class.java, classLoader).toList()
            when {
                providers.isEmpty() -> failure(
                    jar,
                    classLoader,
                    PluginLoadFailureCode.NO_PROVIDER,
                    "JAR does not declare ${EdbPlugin::class.qualifiedName} in META-INF/services."
                )
                providers.size > 1 -> failure(
                    jar,
                    classLoader,
                    PluginLoadFailureCode.MULTIPLE_PROVIDERS,
                    "A plugin JAR must declare exactly one EdbPlugin provider."
                )
                else -> validateCandidate(jar, providers.single(), classLoader)
            }
        } catch (failure: ServiceConfigurationError) {
            failure(jar, classLoader, PluginLoadFailureCode.LOAD_FAILED, "ServiceLoader failed: ${failure.message}", failure)
        } catch (failure: Throwable) {
            failure(jar, classLoader, PluginLoadFailureCode.LOAD_FAILED, "Plugin JAR failed to load: ${failure.message}", failure)
        }
    }

    private fun validateCandidate(jar: Path, plugin: EdbPlugin, classLoader: URLClassLoader): CandidateResult {
        val descriptor = try {
            plugin.descriptor
        } catch (failure: Throwable) {
            return failure(
                jar,
                classLoader,
                PluginLoadFailureCode.INVALID_DESCRIPTOR,
                "Plugin descriptor could not be read: ${failure.message}",
                failure,
                plugin
            )
        }
        if (!isCompatible(descriptor.apiVersion, hostApiVersion)) {
            return failure(
                jar,
                classLoader,
                PluginLoadFailureCode.API_INCOMPATIBLE,
                "Plugin '${descriptor.id}' requires API ${descriptor.apiVersion}; host provides $hostApiVersion.",
                plugin = plugin
            )
        }
        val contributions = try {
            plugin.contributions()
        } catch (failure: Throwable) {
            return failure(
                jar,
                classLoader,
                PluginLoadFailureCode.INVALID_CONTRIBUTION,
                "Plugin '${descriptor.id}' contributions could not be read: ${failure.message}",
                failure,
                plugin
            )
        }
        val validation = validateContributions(contributions)
        if (validation != null) {
            return failure(
                jar,
                classLoader,
                PluginLoadFailureCode.INVALID_CONTRIBUTION,
                "Plugin '${descriptor.id}' $validation",
                plugin = plugin
            )
        }
        return CandidateResult.Success(plugin, descriptor, contributions, classLoader)
    }

    private fun validateContributions(contributions: PluginContributions): String? {
        val tasks = contributions.taskHandlers.map { it.taskType }
        val workflows = contributions.workflowStepHandlers.map { it.type }
        if (tasks.any { !CONTRIBUTION_ID.matches(it) || it.length > 50 }) return "contains an invalid task type."
        if (workflows.any { !CONTRIBUTION_ID.matches(it) }) return "contains an invalid workflow step type."
        if (tasks.distinct().size != tasks.size) return "contains duplicate task types."
        if (workflows.distinct().size != workflows.size) return "contains duplicate workflow step types."
        return null
    }

    private fun duplicateFailure(
        jar: Path,
        pluginId: String,
        contributions: PluginContributions,
        pluginIds: Set<String>,
        taskTypes: Set<String>,
        workflowTypes: Set<String>
    ): PluginLoadFailure? {
        if (pluginId in pluginIds) {
            return PluginLoadFailure(jar, PluginLoadFailureCode.DUPLICATE_PLUGIN, "Plugin id is already loaded: $pluginId")
        }
        contributions.taskHandlers.firstOrNull { it.taskType in taskTypes }?.let {
            return PluginLoadFailure(
                jar,
                PluginLoadFailureCode.DUPLICATE_CONTRIBUTION,
                "Task handler type is already contributed: ${it.taskType}"
            )
        }
        contributions.workflowStepHandlers.firstOrNull { it.type in workflowTypes }?.let {
            return PluginLoadFailure(
                jar,
                PluginLoadFailureCode.DUPLICATE_CONTRIBUTION,
                "Workflow step type is already contributed: ${it.type}"
            )
        }
        return null
    }

    private fun isCompatible(required: String, provided: String): Boolean {
        val (requiredMajor, requiredMinor) = parseApiVersion(required)
        val (providedMajor, providedMinor) = parseApiVersion(provided)
        return requiredMajor == providedMajor && requiredMinor <= providedMinor
    }

    private fun parseApiVersion(value: String): Pair<Int, Int> {
        val parts = value.split('.')
        require(parts.size == 2 && parts.all { it.toIntOrNull() != null }) { "Invalid Plugin API version: $value" }
        return parts[0].toInt() to parts[1].toInt()
    }

    private fun failure(
        jar: Path,
        classLoader: URLClassLoader,
        code: PluginLoadFailureCode,
        message: String,
        cause: Throwable? = null,
        plugin: EdbPlugin? = null
    ): CandidateResult.Failure {
        plugin?.let { runCatching { it.close() } }
        runCatching { classLoader.close() }
        return CandidateResult.Failure(PluginLoadFailure(jar, code, message, cause))
    }

    private fun closeCandidate(plugin: EdbPlugin, classLoader: URLClassLoader) {
        try {
            plugin.close()
        } finally {
            classLoader.close()
        }
    }

    private sealed interface CandidateResult {
        data class Success(
            val plugin: EdbPlugin,
            val descriptor: me.moiclathy.edb.plugin.api.PluginDescriptor,
            val contributions: PluginContributions,
            val classLoader: URLClassLoader
        ) : CandidateResult

        data class Failure(val failure: PluginLoadFailure) : CandidateResult
    }

    companion object {
        private val CONTRIBUTION_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
    }
}
