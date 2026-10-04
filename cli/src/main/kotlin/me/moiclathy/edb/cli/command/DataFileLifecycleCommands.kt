package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import me.moiclathy.edb.cli.service.DataFileApiClient
import me.moiclathy.edb.cli.service.TaskServerConfig

class DataFileDeleteCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "delete", description = "Mark a DataFile deleted and remove its stored content") {
    private val projectId by argument("project-id").int()
    private val dataFileId by argument("data-file-id").int()
    private val confirmed by option(
        "--confirm",
        help = "Confirm deletion of the DataFile and its physical stored content"
    ).flag()

    override fun run() = command(root) { output ->
        require(confirmed) { "DataFile deletion removes stored content. Re-run with --confirm." }
        output.dataFileDeleted(clientFactory(root.taskServerConfig()).delete(projectId, dataFileId))
    }
}

class DataFileOrphanCommand : DescribedCommand(
    name = "orphan",
    description = "Administer orphaned storage content cleanup"
) {
    override fun run() = Unit
}

class DataFileOrphanShowCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "show", description = "Show orphan cleanup policy and state") {
    override fun run() = command(root) { output ->
        output.orphanOverview(clientFactory(root.taskServerConfig()).orphanOverview())
    }
}

class DataFileOrphanEstimateCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "estimate", description = "Scan orphan candidates without deleting files") {
    override fun run() = command(root) { output ->
        output.orphanEstimate(clientFactory(root.taskServerConfig()).orphanEstimate())
    }
}

class DataFileOrphanCleanupCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "cleanup", description = "Permanently delete eligible orphaned storage files") {
    private val confirmed by option(
        "--confirm",
        help = "Confirm permanent deletion of orphaned physical storage files"
    ).flag()

    override fun run() = command(root) { output ->
        require(confirmed) { "Orphan cleanup permanently deletes physical files. Re-run with --confirm." }
        output.orphanCleanup(clientFactory(root.taskServerConfig()).runOrphanCleanup())
    }
}

class DataFileOrphanLastCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "last", description = "Show latest orphan cleanup result") {
    override fun run() = command(root) { output ->
        output.orphanState(clientFactory(root.taskServerConfig()).lastOrphanResult())
    }
}

class DataFileRetentionCommand : DescribedCommand(
    name = "retention",
    description = "Administer retention of deleted DataFile database records"
) {
    override fun run() = Unit
}

class DataFileRetentionShowCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "show", description = "Show DataFile retention policy and state") {
    override fun run() = command(root) { output ->
        output.dataFileRetentionOverview(clientFactory(root.taskServerConfig()).retentionOverview())
    }
}

class DataFileRetentionEstimateCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "estimate", description = "Estimate expired deleted records") {
    override fun run() = command(root) { output ->
        output.dataFileRetentionEstimate(clientFactory(root.taskServerConfig()).retentionEstimate())
    }
}

class DataFileRetentionCleanupCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "cleanup", description = "Permanently remove expired deleted records") {
    private val confirmed by option(
        "--confirm",
        help = "Confirm permanent deletion of expired DataFile database records"
    ).flag()

    override fun run() = command(root) { output ->
        require(confirmed) { "DataFile retention permanently deletes database records. Re-run with --confirm." }
        output.dataFileRetentionCleanup(clientFactory(root.taskServerConfig()).runRetentionCleanup())
    }
}

class DataFileRetentionLastCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "last", description = "Show latest DataFile retention result") {
    override fun run() = command(root) { output ->
        output.dataFileRetentionState(clientFactory(root.taskServerConfig()).lastRetentionResult())
    }
}
