package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.json.JsonObject
import me.moiclathy.edb.cli.service.ProjectConfigurationApiClient
import me.moiclathy.edb.cli.service.ProjectConfigurationFileFormat
import me.moiclathy.edb.cli.service.ProjectConfigurationHistoryApiClient
import me.moiclathy.edb.cli.service.TaskServerConfig
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ProjectConfigurationCommand : DescribedCommand(
    name = "config",
    description = "View, validate, update, import and export project configuration"
) {
    override fun run() = Unit
}

class ProjectConfigurationShowCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectConfigurationApiClient = ::ProjectConfigurationApiClient
) : DescribedCommand(name = "show", description = "Print the current YAML configuration") {
    private val projectId by argument("project-id").int()

    override fun run() = command(root) { output ->
        output.projectConfiguration(projectId, clientFactory(root.taskServerConfig()).show(projectId))
    }
}

class ProjectConfigurationValidateCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectConfigurationApiClient = ::ProjectConfigurationApiClient
) : DescribedCommand(name = "validate", description = "Validate a YAML file without saving it") {
    private val projectId by argument("project-id").int()
    private val file by argument("yaml-file")

    override fun run() = command(root) { output ->
        val result = clientFactory(root.taskServerConfig()).validate(projectId, readFile(file, "YAML configuration"))
        output.configurationValidation(result)
        if (!result.valid) throw ProgramResult(1)
    }
}

class ProjectConfigurationUpdateCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectConfigurationApiClient = ::ProjectConfigurationApiClient
) : DescribedCommand(name = "update", description = "Replace project configuration with a YAML file") {
    private val projectId by argument("project-id").int()
    private val file by argument("yaml-file")
    private val confirmed by option("--confirm", help = "Confirm project configuration replacement").flag()

    override fun run() = command(root) { output ->
        require(confirmed) { "Configuration update replaces the current project configuration. Re-run with --confirm." }
        output.configurationMutation(
            "updated",
            clientFactory(root.taskServerConfig()).update(projectId, readFile(file, "YAML configuration"))
        )
    }
}

class ProjectConfigurationExportCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectConfigurationApiClient = ::ProjectConfigurationApiClient
) : DescribedCommand(name = "export", description = "Export project configuration as YAML or EDB package") {
    private val projectId by argument("project-id").int()
    private val format by option("--format", help = "yaml or edb").default("yaml")
    private val outputPath by option("--output", "-o")
    private val force by option("--force", help = "Replace an existing export file").flag()

    override fun run() = command(root) { output ->
        val selected = runCatching { ProjectConfigurationFileFormat.valueOf(format.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Export format must be yaml or edb.") }
        val target = Path.of(outputPath ?: "project-$projectId.${selected.name.lowercase()}")
        output.configurationExported(
            clientFactory(root.taskServerConfig()).export(projectId, selected, target, force),
            selected
        )
    }
}

class ProjectConfigurationImportCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectConfigurationApiClient = ::ProjectConfigurationApiClient
) : DescribedCommand(name = "import", description = "Import YAML or an EDB package into a project") {
    private val projectId by argument("project-id").int()
    private val file by argument("file")
    private val confirmed by option("--confirm", help = "Confirm configuration import and replacement").flag()

    override fun run() = command(root) { output ->
        require(confirmed) { "Configuration import modifies the project. Re-run with --confirm." }
        val path = Path.of(file)
        require(Files.isRegularFile(path)) { "Configuration import file does not exist: ${path.toAbsolutePath()}" }
        val result: JsonObject = when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
            "edb" -> clientFactory(root.taskServerConfig()).importPackage(projectId, path)
            "yaml", "yml" -> clientFactory(root.taskServerConfig()).importYaml(
                projectId,
                Files.readString(path, StandardCharsets.UTF_8).removePrefix("\uFEFF")
            )
            else -> throw IllegalArgumentException("Import file must use .yaml, .yml, or .edb extension.")
        }
        output.configurationMutation("imported", result)
    }
}

private fun readFile(value: String, label: String): String {
    val path = Path.of(value)
    require(Files.isRegularFile(path)) { "$label file does not exist: ${path.toAbsolutePath()}" }
    return Files.readString(path, StandardCharsets.UTF_8).removePrefix("\uFEFF")
}

class ProjectConfigurationHistoryCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectConfigurationHistoryApiClient = ::ProjectConfigurationHistoryApiClient
) : DescribedCommand(name = "history", description = "List configuration revisions") {
    private val projectId by argument("project-id").int()

    override fun run() = command(root) { output ->
        output.configurationHistory(clientFactory(root.taskServerConfig()).history(projectId))
    }
}

class ProjectConfigurationDiffCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectConfigurationHistoryApiClient = ::ProjectConfigurationHistoryApiClient
) : DescribedCommand(name = "diff", description = "Compare two configuration revisions") {
    private val projectId by argument("project-id").int()
    private val from by argument("from-commit")
    private val to by argument("to-commit")

    override fun run() = command(root) { output ->
        output.configurationDiff(clientFactory(root.taskServerConfig()).diff(projectId, from, to))
    }
}

class ProjectConfigurationRestoreCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> ProjectConfigurationHistoryApiClient = ::ProjectConfigurationHistoryApiClient
) : DescribedCommand(name = "restore", description = "Restore configuration from a revision") {
    private val projectId by argument("project-id").int()
    private val commitId by argument("commit-id")
    private val confirmed by option("--confirm", help = "Confirm configuration restoration").flag()

    override fun run() = command(root) { output ->
        require(confirmed) { "Configuration restore modifies the project. Re-run with --confirm." }
        output.configurationRestored(clientFactory(root.taskServerConfig()).restore(projectId, commitId))
    }
}
