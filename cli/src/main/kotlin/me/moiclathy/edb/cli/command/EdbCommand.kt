package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import me.moiclathy.edb.cli.service.TaskServerConfig
import java.nio.file.Path

abstract class DescribedCommand(
    name: String,
    private val description: String
) : CliktCommand(name = name) {
    override fun help(context: Context): String = description
}

class EdbCommand : DescribedCommand(
    name = "edb",
    description = "Experiment Data Bridge command console"
) {
    override val invokeWithoutSubcommand: Boolean = true
    override val printHelpOnEmptyArgs: Boolean = true
    private val plugins by option("--plugins", help = "Plugin JAR directory")
        .default(System.getenv("EDB_PLUGIN_DIRECTORY") ?: "./plugins")
    private val workflows by option("--workflows", help = "JSON Workflow directory")
        .default(System.getenv("EDB_WORKFLOW_DIRECTORY") ?: "./config/workflows")
    private val server by option("--server", help = "EDB server base URL")
        .default(System.getenv("EDB_SERVER_URL") ?: "http://localhost:25560")
    private val token by option("--token", help = "Bearer token (or set EDB_TOKEN)")
        .default(System.getenv("EDB_TOKEN") ?: "")
    private val requestTimeoutMs by option("--request-timeout-ms", help = "HTTP request timeout in milliseconds")
        .long()
        .default(System.getenv("EDB_REQUEST_TIMEOUT_MS")?.toLongOrNull() ?: 30_000L)
    private val json by option("--json", help = "Emit machine-readable JSON").flag(default = false)

    val pluginDirectory: Path get() = Path.of(plugins)
    val workflowDirectory: Path get() = Path.of(workflows)
    val jsonOutput: Boolean get() = json
    fun taskServerConfig(): TaskServerConfig = TaskServerConfig(server, token, requestTimeoutMs)

    override fun run() = Unit
}
