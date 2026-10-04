package me.moiclathy.edb.plugin.api

interface EdbPlugin : AutoCloseable {
    val descriptor: PluginDescriptor

    fun initialize(context: PluginContext) = Unit

    fun contributions(): PluginContributions = PluginContributions.EMPTY

    override fun close() = Unit
}

data class PluginContext(
    val properties: Map<String, String> = emptyMap(),
    val logger: PluginLogger = PluginLogger.NO_OP
)

fun interface PluginLogger {
    fun log(level: PluginLogLevel, message: String, failure: Throwable?)

    companion object {
        val NO_OP = PluginLogger { _, _, _ -> }
    }
}

enum class PluginLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

data class PluginContributions(
    val taskHandlers: List<PluginTaskHandler> = emptyList(),
    val workflowStepHandlers: List<PluginWorkflowStepHandler> = emptyList()
) {
    companion object {
        val EMPTY = PluginContributions()
    }
}
