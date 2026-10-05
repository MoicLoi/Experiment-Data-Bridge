package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import me.moiclathy.edb.cli.service.PluginCliService
import java.nio.file.Path

class PluginCommand : DescribedCommand(name = "plugin", description = "Manage local plugin JARs") {
    override fun run() = Unit
}

class PluginListCommand(
    private val root: EdbCommand,
    private val service: PluginCliService = PluginCliService()
) : DescribedCommand(name = "list", description = "List discovered plugins") {
    override fun run() = command(root) { output ->
        val scan = service.list(root.pluginDirectory)
        output.pluginList(scan.plugins, scan.failures)
        if (scan.failures.isNotEmpty()) throw ProgramResult(2)
    }
}

class PluginInspectCommand(
    private val root: EdbCommand,
    private val service: PluginCliService = PluginCliService()
) : DescribedCommand(name = "inspect", description = "Inspect one plugin") {
    private val pluginId by argument("id")

    override fun run() = command(root) { output ->
        val scan = service.list(root.pluginDirectory)
        if (scan.failures.isNotEmpty()) {
            output.pluginList(scan.plugins, scan.failures)
            throw ProgramResult(2)
        }
        val plugin = scan.plugins.firstOrNull { it.id == pluginId }
            ?: run {
                output.error("Plugin is not loaded: $pluginId")
                throw ProgramResult(1)
            }
        output.pluginDetail(plugin)
    }
}

class PluginValidateCommand(
    private val root: EdbCommand,
    private val service: PluginCliService = PluginCliService()
) : DescribedCommand(name = "validate", description = "Validate one plugin JAR") {
    private val jar by argument("jar")

    override fun run() = command(root) { output ->
        val scan = service.validate(Path.of(jar))
        if (scan.failures.isNotEmpty() || scan.plugins.size != 1) {
            output.pluginList(scan.plugins, scan.failures)
            throw ProgramResult(2)
        }
        output.pluginValidated(scan.plugins.single())
    }
}

internal inline fun CliktCommand.command(root: EdbCommand, block: (EdbCliOutput) -> Unit) {
    val output = EdbCliOutput(
        json = root.jsonOutput,
        stdout = { echo(it) },
        stderr = { echo(it, err = true) }
    )
    try {
        block(output)
    } catch (result: ProgramResult) {
        throw result
    } catch (failure: Exception) {
        output.error(failure.message ?: failure::class.simpleName.orEmpty())
        throw ProgramResult(2)
    }
}
