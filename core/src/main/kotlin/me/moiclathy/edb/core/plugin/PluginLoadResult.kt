package me.moiclathy.edb.core.plugin

import me.moiclathy.edb.plugin.api.EdbPlugin
import me.moiclathy.edb.plugin.api.PluginContributions
import me.moiclathy.edb.plugin.api.PluginDescriptor
import java.net.URLClassLoader
import java.nio.file.Path

enum class PluginLoadFailureCode {
    NO_PROVIDER,
    MULTIPLE_PROVIDERS,
    INVALID_DESCRIPTOR,
    API_INCOMPATIBLE,
    INVALID_CONTRIBUTION,
    DUPLICATE_PLUGIN,
    DUPLICATE_CONTRIBUTION,
    INITIALIZATION_FAILED,
    LOAD_FAILED
}

data class PluginLoadFailure(
    val jar: Path,
    val code: PluginLoadFailureCode,
    val message: String,
    val cause: Throwable? = null
)

class LoadedPlugin internal constructor(
    val jar: Path,
    val plugin: EdbPlugin,
    val descriptor: PluginDescriptor,
    val contributions: PluginContributions,
    private val classLoader: URLClassLoader
) : AutoCloseable {
    override fun close() {
        try {
            plugin.close()
        } finally {
            classLoader.close()
        }
    }
}

class PluginLoadResult(
    val directory: Path,
    val loaded: List<LoadedPlugin>,
    val failures: List<PluginLoadFailure>
) : AutoCloseable {
    override fun close() {
        var firstFailure: Throwable? = null
        loaded.asReversed().forEach { plugin ->
            try {
                plugin.close()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure!!.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw it }
    }
}
