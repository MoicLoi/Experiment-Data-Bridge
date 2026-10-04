package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import me.moiclathy.edb.cli.service.TaskServerConfig
import me.moiclathy.edb.cli.service.TemplateApiClient
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class TemplateCommand : DescribedCommand(name = "template", description = "Manage reusable configuration templates") {
    override fun run() = Unit
}

class TemplateListCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TemplateApiClient = ::TemplateApiClient
) : DescribedCommand(name = "list", description = "List visible templates") {
    private val keyword by option("--keyword")
    private val type by option("--type")
    private val visibility by option("--visibility")
    override fun run() = command(root) { it.templateList(clientFactory(root.taskServerConfig()).list(keyword, type, visibility)) }
}

class TemplateInfoCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TemplateApiClient = ::TemplateApiClient
) : DescribedCommand(name = "info", description = "Show one template") {
    private val id by argument("id").long()
    override fun run() = command(root) { it.templateDetail(clientFactory(root.taskServerConfig()).info(id)) }
}

class TemplateCreateCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TemplateApiClient = ::TemplateApiClient
) : DescribedCommand(name = "create", description = "Create a template from a YAML file") {
    private val file by argument("file.yaml")
    private val name by option("--name")
    private val description by option("--description").default("")
    private val type by option("--type").default("EXPERIMENT")
    private val visibility by option("--visibility").default("PRIVATE")
    override fun run() = command(root) { output ->
        val path = templateFile(file)
        val inferredName = path.fileName.toString().substringBeforeLast('.').ifBlank { "template" }
        output.templateCreated(clientFactory(root.taskServerConfig()).create(
            name ?: inferredName, description, type, visibility, readTemplate(path)
        ))
    }
}

class TemplateUpdateCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TemplateApiClient = ::TemplateApiClient
) : DescribedCommand(name = "update", description = "Update a template from a YAML file") {
    private val id by argument("id").long()
    private val file by argument("file.yaml")
    private val name by option("--name")
    private val description by option("--description")
    private val type by option("--type")
    private val visibility by option("--visibility")
    override fun run() = command(root) { output ->
        output.templateUpdated(clientFactory(root.taskServerConfig()).update(
            id, readTemplate(templateFile(file)), name, description, type, visibility
        ))
    }
}

class TemplateDeleteCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TemplateApiClient = ::TemplateApiClient
) : DescribedCommand(name = "delete", description = "Delete a template") {
    private val id by argument("id").long()
    private val confirmed by option("--confirm", help = "Confirm template deletion").flag()
    override fun run() = command(root) {
        require(confirmed) { "Template deletion is permanent. Re-run with --confirm." }
        clientFactory(root.taskServerConfig()).delete(id)
        it.templateDeleted(id)
    }
}

private fun templateFile(value: String): Path = Path.of(value).also {
    require(Files.isRegularFile(it)) { "Template YAML file does not exist: ${it.toAbsolutePath()}" }
    require(it.fileName.toString().endsWith(".yaml", true) || it.fileName.toString().endsWith(".yml", true)) {
        "Template file must use .yaml or .yml extension."
    }
}

private fun readTemplate(path: Path) = Files.readString(path, StandardCharsets.UTF_8).removePrefix("\uFEFF").also {
    require(it.isNotBlank()) { "Template YAML must not be blank." }
}
