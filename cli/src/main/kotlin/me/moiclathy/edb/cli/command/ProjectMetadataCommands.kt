package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import me.moiclathy.edb.cli.service.ProjectMetadataApiClient
import me.moiclathy.edb.cli.service.TaskServerConfig

class ProjectMetadataCommand : DescribedCommand(name = "metadata", description = "Manage project configuration metadata") {
    override fun run() = Unit
}

class ProjectMetadataShowCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectMetadataApiClient = ::ProjectMetadataApiClient
) : DescribedCommand(name = "show", description = "Show project configuration metadata") {
    private val projectId by argument("project-id").int()
    override fun run() = command(root) { it.projectMetadata(clientFactory(root.taskServerConfig()).show(projectId)) }
}

class ProjectMetadataSetCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectMetadataApiClient = ::ProjectMetadataApiClient
) : DescribedCommand(name = "set", description = "Create or update project configuration metadata") {
    private val projectId by argument("project-id").int()
    private val description by option("--description")
    private val category by option("--category")
    private val dataSource by option("--data-source")
    override fun run() = command(root) {
        it.projectMetadataSet(clientFactory(root.taskServerConfig()).set(projectId, description, category, dataSource))
    }
}

class ProjectMetadataDeleteCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectMetadataApiClient = ::ProjectMetadataApiClient
) : DescribedCommand(name = "delete", description = "Delete project configuration metadata") {
    private val projectId by argument("project-id").int()
    private val confirmed by option("--confirm", help = "Confirm metadata deletion").flag()
    override fun run() = command(root) {
        require(confirmed) { "Metadata deletion is permanent. Re-run with --confirm." }
        clientFactory(root.taskServerConfig()).delete(projectId)
        it.projectMetadataDeleted(projectId)
    }
}

class ProjectTagCommand : DescribedCommand(name = "tag", description = "Manage project configuration tags") {
    override fun run() = Unit
}

class ProjectTagListCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectMetadataApiClient = ::ProjectMetadataApiClient
) : DescribedCommand(name = "list", description = "List project configuration tags") {
    private val projectId by argument("project-id").int()
    override fun run() = command(root) { it.projectTags(clientFactory(root.taskServerConfig()).listTags(projectId)) }
}

class ProjectTagAddCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectMetadataApiClient = ::ProjectMetadataApiClient
) : DescribedCommand(name = "add", description = "Add a project configuration tag") {
    private val projectId by argument("project-id").int()
    private val tag by argument("tag")
    override fun run() = command(root) { it.projectTags(clientFactory(root.taskServerConfig()).addTag(projectId, tag)) }
}

class ProjectTagRemoveCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectMetadataApiClient = ::ProjectMetadataApiClient
) : DescribedCommand(name = "remove", description = "Remove a project configuration tag") {
    private val projectId by argument("project-id").int()
    private val tag by argument("tag")
    override fun run() = command(root) { it.projectTags(clientFactory(root.taskServerConfig()).removeTag(projectId, tag)) }
}
