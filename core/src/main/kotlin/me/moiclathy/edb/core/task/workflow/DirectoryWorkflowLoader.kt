package me.moiclathy.edb.core.task.workflow

import me.moiclathy.edb.core.task.pipeline.Pipeline
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.stream.Collectors

data class WorkflowDirectoryLoadResult(
    val directory: Path,
    val files: List<Path>,
    val pipelineIds: List<String>

)

class WorkflowDirectoryException(message: String) : IllegalArgumentException(message)

class WorkflowFileLoadException(
    val file: Path,
    cause: Throwable
) : IllegalArgumentException(
    "Failed to load workflow file $file: ${cause.message ?: cause::class.simpleName}",
    cause
)

class WorkflowRegistrationConflictException(
    val pipelineIds: Set<String>
) : IllegalArgumentException(
    "Workflow pipeline ids are duplicated or already registered: ${pipelineIds.sorted().joinToString()}"
)

/**
 * Loads only direct `*.workflow.json` children of a directory.
 *
 * Every file is parsed and compiled before registry mutation starts, so malformed definitions do
 * not leave a partially loaded directory behind. Symbolic links and nested directories are ignored.
 */
class DirectoryWorkflowLoader(
    private val driver: JsonWorkflowDriver,
    private val pipelineRegistry: PipelineRegistry
) {
    fun load(directory: Path): WorkflowDirectoryLoadResult {
        val normalizedDirectory = directory.toAbsolutePath().normalize()
        requireDirectory(normalizedDirectory)
        val files = workflowFiles(normalizedDirectory)
        val compiled = files.map { file -> compile(file) }

        val duplicateIds = compiled.groupingBy { it.id }.eachCount()
            .filterValues { it > 1 }
            .keys
        val existingIds = compiled.mapTo(mutableSetOf()) { it.id }
            .intersect(pipelineRegistry.registeredIds())
        val conflicts = duplicateIds + existingIds
        if (conflicts.isNotEmpty()) {
            throw WorkflowRegistrationConflictException(conflicts)
        }

        compiled.forEach(pipelineRegistry::register)
        return WorkflowDirectoryLoadResult(
            directory = normalizedDirectory,
            files = files.toList(),
            pipelineIds = compiled.map(Pipeline::id)
        )
    }

    private fun requireDirectory(directory: Path) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw WorkflowDirectoryException("Workflow directory does not exist: $directory")
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw WorkflowDirectoryException("Workflow path is not a directory: $directory")
        }
    }

    private fun workflowFiles(directory: Path): List<Path> = try {
        Files.list(directory).use { paths ->
            paths
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .filter { it.fileName.toString().endsWith(WORKFLOW_FILE_SUFFIX) }
                .sorted(compareBy { it.fileName.toString() })
                .collect(Collectors.toList())
        }
    } catch (failure: Exception) {
        throw WorkflowDirectoryException(
            "Failed to list workflow directory $directory: ${failure.message ?: failure::class.simpleName}"
        )
    }

    private fun compile(file: Path): Pipeline = try {
        val json = Files.readString(file, StandardCharsets.UTF_8).removePrefix(UTF_8_BOM)
        driver.compile(json)
    } catch (failure: Exception) {
        throw WorkflowFileLoadException(file, failure)
    }

    companion object {
        const val WORKFLOW_FILE_SUFFIX = ".workflow.json"
        private const val UTF_8_BOM = "\uFEFF"
    }
}

