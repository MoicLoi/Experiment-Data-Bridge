package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import me.moiclathy.edb.cli.service.ProjectApiClient
import me.moiclathy.edb.cli.service.TaskServerConfig
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ProjectCommand : DescribedCommand(name = "project", description = "Discover and manage EDB projects") {
    override fun run() = Unit
}

class ProjectListCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectApiClient = ::ProjectApiClient
) : DescribedCommand(name = "list", description = "List projects owned by the current user") {
    override fun run() = command(root) { output ->
        output.projectList(clientFactory(root.taskServerConfig()).list())
    }
}

class ProjectInfoCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectApiClient = ::ProjectApiClient
) : DescribedCommand(name = "info", description = "Show one project") {
    private val projectId by argument("project-id").int()

    override fun run() = command(root) { output ->
        output.projectDetail(clientFactory(root.taskServerConfig()).get(projectId))
    }
}

class ProjectCreateCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectApiClient = ::ProjectApiClient
) : DescribedCommand(name = "create", description = "Create a project") {
    private val name by argument("name")
    private val description by option("--description")
    private val mode by option("--mode", help = "PERSONAL or COLLABORATIVE").default("PERSONAL")
    private val configFile by option("--config-file", help = "Optional UTF-8 YAML configuration file")

    override fun run() = command(root) { output ->
        val yaml = configFile?.let { file ->
            val path = Path.of(file)
            require(Files.isRegularFile(path)) { "Project configuration file does not exist: ${path.toAbsolutePath()}" }
            Files.readString(path, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        }
        output.projectCreated(
            clientFactory(root.taskServerConfig()).create(name, description, mode, yaml)
        )
    }
}

class ProjectDeleteCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectApiClient = ::ProjectApiClient
) : DescribedCommand(name = "delete", description = "Permanently delete a project") {
    private val projectId by argument("project-id").int()
    private val confirmed by option("--confirm", help = "Confirm permanent project deletion").flag()

    override fun run() = command(root) { output ->
        require(confirmed) { "Project deletion is permanent. Re-run with --confirm." }
        clientFactory(root.taskServerConfig()).delete(projectId)
        output.projectDeleted(projectId)
    }
}

class ProjectUpdateCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectApiClient = ::ProjectApiClient
) : DescribedCommand(name = "update", description = "Update project name, description or mode") {
    private val projectId by argument("project-id").int()
    private val name by option("--name", help = "New project name")
    private val description by option("--description", help = "New project description")
    private val mode by option("--mode", help = "PERSONAL or COLLABORATIVE")

    override fun run() = command(root) { output ->
        output.projectUpdated(
            clientFactory(root.taskServerConfig()).update(projectId, name, description, mode)
        )
    }
}

class ProjectReloadCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectApiClient = ::ProjectApiClient
) : DescribedCommand(name = "reload", description = "Reload project configuration on the server") {
    private val projectId by argument("project-id").int()

    override fun run() = command(root) { output ->
        clientFactory(root.taskServerConfig()).reload(projectId)
        output.projectReloaded(projectId)
    }
}
