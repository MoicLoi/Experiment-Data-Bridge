package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import me.moiclathy.edb.cli.service.TaskApiClient
import me.moiclathy.edb.cli.service.TaskServerConfig

class RetentionCommand : DescribedCommand(name = "retention", description = "Administer terminal task retention") {
    override fun run() = Unit
}

class RetentionShowCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "show", description = "Show retention policy and manager state") {
    override fun run() = command(root) { output ->
        output.retentionOverview(clientFactory(root.taskServerConfig()).retentionOverview())
    }
}

class RetentionEstimateCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "estimate", description = "Estimate expired tasks without deleting data") {
    override fun run() = command(root) { output ->
        output.retentionEstimate(clientFactory(root.taskServerConfig()).retentionEstimate())
    }
}

class RetentionCleanupCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "cleanup", description = "Permanently delete expired terminal tasks") {
    private val confirmed by option(
        "--confirm",
        help = "Confirm permanent deletion of all tasks selected by the server retention policy"
    ).flag()

    override fun run() = command(root) { output ->
        require(confirmed) { "Cleanup permanently deletes expired tasks. Re-run with --confirm." }
        output.retentionCleanup(clientFactory(root.taskServerConfig()).runRetentionCleanup())
    }
}

class RetentionLastCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "last", description = "Show running state and latest cleanup result") {
    override fun run() = command(root) { output ->
        output.retentionState(clientFactory(root.taskServerConfig()).lastRetentionResult())
    }
}
