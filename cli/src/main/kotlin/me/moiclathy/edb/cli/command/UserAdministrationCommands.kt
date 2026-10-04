package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import me.moiclathy.edb.cli.service.TaskServerConfig
import me.moiclathy.edb.cli.service.UserAdministrationApiClient

class OpCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> UserAdministrationApiClient = ::UserAdministrationApiClient
) : DescribedCommand(name = "op", description = "Grant operator privileges to an account") {
    private val username by argument("username")

    override fun run() = command(root) { output ->
        output.operatorChanged(clientFactory(root.taskServerConfig()).op(username), granted = true)
    }
}

class DeopCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> UserAdministrationApiClient = ::UserAdministrationApiClient
) : DescribedCommand(name = "deop", description = "Remove operator privileges from an account") {
    private val username by argument("username")
    private val confirmed by option("--confirm", help = "Confirm operator privilege removal").flag()

    override fun run() = command(root) { output ->
        require(confirmed) { "Removing operator privileges may revoke active access. Re-run with --confirm." }
        output.operatorChanged(clientFactory(root.taskServerConfig()).deop(username), granted = false)
    }
}
