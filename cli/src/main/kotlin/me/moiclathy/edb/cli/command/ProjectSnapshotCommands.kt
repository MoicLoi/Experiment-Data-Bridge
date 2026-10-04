package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import me.moiclathy.edb.cli.service.ProjectSnapshotApiClient
import me.moiclathy.edb.cli.service.TaskServerConfig

class ProjectSnapshotCommand : DescribedCommand(name = "snapshot", description = "Manage project configuration snapshots") {
    override fun run() = Unit
}

class ProjectSnapshotCreateCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectSnapshotApiClient = ::ProjectSnapshotApiClient
) : DescribedCommand(name = "create", description = "Snapshot the current project configuration") {
    private val projectId by argument("project-id").int()
    private val description by option("--description")
    override fun run() = command(root) { it.snapshotCreated(clientFactory(root.taskServerConfig()).create(projectId, description)) }
}

class ProjectSnapshotListCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectSnapshotApiClient = ::ProjectSnapshotApiClient
) : DescribedCommand(name = "list", description = "List project snapshots") {
    private val projectId by argument("project-id").int()
    override fun run() = command(root) { it.snapshotList(clientFactory(root.taskServerConfig()).list(projectId)) }
}

class ProjectSnapshotLatestCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectSnapshotApiClient = ::ProjectSnapshotApiClient
) : DescribedCommand(name = "latest", description = "Show the latest project snapshot") {
    private val projectId by argument("project-id").int()
    override fun run() = command(root) { it.snapshotDetail(clientFactory(root.taskServerConfig()).latest(projectId)) }
}

class ProjectSnapshotInfoCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectSnapshotApiClient = ::ProjectSnapshotApiClient
) : DescribedCommand(name = "info", description = "Show one configuration snapshot") {
    private val snapshotId by argument("snapshot-id").long()
    override fun run() = command(root) { it.snapshotDetail(clientFactory(root.taskServerConfig()).info(snapshotId)) }
}

class ProjectSnapshotCompareCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectSnapshotApiClient = ::ProjectSnapshotApiClient
) : DescribedCommand(name = "compare", description = "Compare two project snapshots") {
    private val projectId by argument("project-id").int()
    private val sourceId by argument("source-id").long()
    private val targetId by argument("target-id").long()
    override fun run() = command(root) { it.snapshotCompare(clientFactory(root.taskServerConfig()).compare(projectId, sourceId, targetId)) }
}

class ProjectSnapshotMergeCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectSnapshotApiClient = ::ProjectSnapshotApiClient
) : DescribedCommand(name = "merge", description = "Merge a source snapshot into a target snapshot") {
    private val projectId by argument("project-id").int()
    private val sourceId by argument("source-id").long()
    private val targetId by argument("target-id").long()
    private val strategy by option("--strategy").default("FAIL_ON_CONFLICT")
    override fun run() = command(root) { output ->
        val result = clientFactory(root.taskServerConfig()).merge(projectId, sourceId, targetId, strategy)
        output.snapshotMerge(result)
        if (!result.success) throw ProgramResult(1)
    }
}

class ProjectSnapshotDeleteCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectSnapshotApiClient = ::ProjectSnapshotApiClient
) : DescribedCommand(name = "delete", description = "Delete a configuration snapshot") {
    private val snapshotId by argument("snapshot-id").long()
    private val confirmed by option("--confirm", help = "Confirm snapshot deletion").flag()
    override fun run() = command(root) {
        require(confirmed) { "Snapshot deletion is permanent. Re-run with --confirm." }
        clientFactory(root.taskServerConfig()).delete(snapshotId)
        it.snapshotDeleted(snapshotId)
    }
}
