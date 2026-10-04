package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import me.moiclathy.edb.cli.service.DataFileApiClient
import me.moiclathy.edb.cli.service.TaskServerConfig
import me.moiclathy.edb.cli.service.TaskSubmissionCliOptions
import java.nio.file.Path

class DataFileCommand : DescribedCommand(name = "datafile", description = "Manage project data files and conversions") {
    override fun run() = Unit
}

class DataFileUploadCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "upload", description = "Stream a local file to a project") {
    private val projectId by argument("project-id").int()
    private val file by argument("file")
    private val mediaType by option("--media-type", help = "Override detected media type")

    override fun run() = command(root) { output ->
        output.dataFileUploaded(clientFactory(root.taskServerConfig()).upload(projectId, Path.of(file), mediaType))
    }
}

class DataFileListCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "list", description = "List project data files") {
    private val projectId by argument("project-id").int()

    override fun run() = command(root) { output ->
        output.dataFileList(clientFactory(root.taskServerConfig()).list(projectId))
    }
}

class DataFileInfoCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "info", description = "Show one data file descriptor") {
    private val projectId by argument("project-id").int()
    private val dataFileId by argument("data-file-id").int()

    override fun run() = command(root) { output ->
        output.dataFileDetail(clientFactory(root.taskServerConfig()).get(projectId, dataFileId))
    }
}

class DataFileDownloadCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "download", description = "Stream a stored data file to disk") {
    private val projectId by argument("project-id").int()
    private val dataFileId by argument("data-file-id").int()
    private val outputPath by option("--output", "-o", help = "Output path; defaults to the original file name")
    private val force by option("--force", help = "Replace an existing output file").flag()

    override fun run() = command(root) { output ->
        val client = clientFactory(root.taskServerConfig())
        val descriptor = client.get(projectId, dataFileId)
        val target = outputPath?.let(Path::of) ?: Path.of(safeFileName(descriptor.originalFileName, dataFileId))
        output.dataFileDownloaded(client.download(projectId, dataFileId, target, force))
    }

    private fun safeFileName(value: String, id: Int): String = value
        .replace(Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]"), "_")
        .trim()
        .takeIf { it.isNotEmpty() && it != "." && it != ".." }
        ?: "datafile-$id.bin"
}

class DataFileConvertCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : TaskSubmissionCommand(name = "convert", description = "Submit a data file conversion task") {
    private val projectId by argument("project-id").int()
    private val dataFileId by argument("data-file-id").int()
    private val targetFormat by argument("target-format")
    private val options by option("--option", help = "Converter option as key=value").multiple()

    override fun run() = command(root) { output ->
        output.taskSubmitted(
            clientFactory(root.taskServerConfig()).submitConversion(
                projectId, dataFileId, targetFormat, keyValues(options, "Converter option"), executionOptions()
            )
        )
    }
}

class DataFileTargetsCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "targets", description = "List available conversion targets") {
    private val projectId by argument("project-id").int()
    private val dataFileId by argument("data-file-id").int()

    override fun run() = command(root) { output ->
        output.conversionTargets(clientFactory(root.taskServerConfig()).conversionTargets(projectId, dataFileId))
    }
}

class DataFileConversionsCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "conversions", description = "List conversion artifacts derived from a source file") {
    private val projectId by argument("project-id").int()
    private val dataFileId by argument("data-file-id").int()

    override fun run() = command(root) { output ->
        output.dataFileArtifacts(
            "conversion",
            clientFactory(root.taskServerConfig()).conversionArtifacts(projectId, dataFileId)
        )
    }
}

class AnalysisCommand : DescribedCommand(name = "analysis", description = "Discover handlers and manage analysis tasks and artifacts") {
    override fun run() = Unit
}

class AnalysisSubmitCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : TaskSubmissionCommand(name = "submit", description = "Submit an analysis task") {
    private val projectId by argument("project-id").int()
    private val handlerId by argument("handler-id")
    private val sources by option("--source", help = "Source DataFile ID; repeat for multiple files").int().multiple()
    private val parameters by option("--parameter", help = "Analysis parameter as key=value").multiple()

    override fun run() = command(root) { output ->
        output.taskSubmitted(
            clientFactory(root.taskServerConfig()).submitAnalysis(
                projectId, handlerId, sources.toSet(), keyValues(parameters, "Analysis parameter"), executionOptions()
            )
        )
    }
}

class AnalysisHandlersCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "handlers", description = "List registered analysis handlers") {
    private val projectId by argument("project-id").int()

    override fun run() = command(root) { output ->
        output.analysisHandlers(clientFactory(root.taskServerConfig()).analysisHandlers(projectId))
    }
}

class AnalysisArtifactsCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "artifacts", description = "List analysis artifacts derived from a source file") {
    private val projectId by argument("project-id").int()
    private val dataFileId by argument("data-file-id").int()

    override fun run() = command(root) { output ->
        output.dataFileArtifacts(
            "analysis",
            clientFactory(root.taskServerConfig()).analysisArtifacts(projectId, dataFileId)
        )
    }
}

class ReportCommand : DescribedCommand(name = "report", description = "Discover definitions and manage report tasks and artifacts") {
    override fun run() = Unit
}

class ReportSubmitCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : TaskSubmissionCommand(name = "submit", description = "Submit a report task") {
    private val projectId by argument("project-id").int()
    private val definitionId by argument("definition-id")
    private val sources by option("--source", help = "Source DataFile ID; repeat for multiple files").int().multiple()
    private val parameters by option("--parameter", help = "Report parameter as key=value").multiple()

    override fun run() = command(root) { output ->
        output.taskSubmitted(
            clientFactory(root.taskServerConfig()).submitReport(
                projectId, definitionId, sources.toSet(), keyValues(parameters, "Report parameter"), executionOptions()
            )
        )
    }
}

class ReportDefinitionsCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "definitions", description = "List available report definitions") {
    private val projectId by argument("project-id").int()

    override fun run() = command(root) { output ->
        output.reportDefinitions(clientFactory(root.taskServerConfig()).reportDefinitions(projectId))
    }
}

class ReportArtifactsCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> DataFileApiClient = ::DataFileApiClient
) : DescribedCommand(name = "artifacts", description = "List report artifacts derived from a source file") {
    private val projectId by argument("project-id").int()
    private val dataFileId by argument("data-file-id").int()

    override fun run() = command(root) { output ->
        output.dataFileArtifacts(
            "report",
            clientFactory(root.taskServerConfig()).reportArtifacts(projectId, dataFileId)
        )
    }
}

abstract class TaskSubmissionCommand(name: String, description: String) : DescribedCommand(name, description) {
    private val priority by option("--priority").int().default(50)
    private val maxAttempts by option("--max-attempts").int().default(1)
    private val initialDelayMs by option("--initial-delay-ms").long().default(1_000)
    private val backoffMultiplier by option("--backoff").double().default(2.0)
    private val maxDelayMs by option("--max-delay-ms").long().default(60_000)
    private val timeoutMs by option("--timeout-ms").long()

    protected fun executionOptions() = TaskSubmissionCliOptions(
        priority, maxAttempts, initialDelayMs, backoffMultiplier, maxDelayMs, timeoutMs
    )

    protected fun keyValues(values: List<String>, label: String): Map<String, String> = values.associate { value ->
        val separator = value.indexOf('=')
        require(separator > 0) { "$label must use key=value: $value" }
        value.substring(0, separator).trim().also { require(it.isNotEmpty()) { "$label key must not be blank." } } to
            value.substring(separator + 1)
    }
}
