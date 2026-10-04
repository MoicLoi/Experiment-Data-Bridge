package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import me.moiclathy.edb.cli.service.WorkflowCliService
import java.nio.file.Path

class WorkflowCommand : DescribedCommand(name = "workflow", description = "Inspect JSON workflows") {
    override fun run() = Unit
}

class WorkflowListCommand(
    private val root: EdbCommand,
    private val service: WorkflowCliService = WorkflowCliService()
) : DescribedCommand(name = "list", description = "List and validate workflow directory") {
    override fun run() = command(root) { output ->
        output.workflowList(service.list(root.workflowDirectory, root.pluginDirectory))
    }
}

class WorkflowValidateCommand(
    private val root: EdbCommand,
    private val service: WorkflowCliService = WorkflowCliService()
) : DescribedCommand(name = "validate", description = "Validate one workflow JSON file") {
    private val file by argument("file")

    override fun run() = command(root) { output ->
        output.workflowValidated(service.validate(Path.of(file), root.pluginDirectory))
    }
}
