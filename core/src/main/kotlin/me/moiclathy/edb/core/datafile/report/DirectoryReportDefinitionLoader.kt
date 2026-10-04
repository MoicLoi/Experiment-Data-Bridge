package me.moiclathy.edb.core.datafile.report

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.stream.Collectors

data class ReportDefinitionDirectoryLoadResult(
    val directory: Path,
    val files: List<Path>,
    val definitionIds: List<String>
)

class ReportDefinitionDirectoryException(message: String) : IllegalArgumentException(message)

class ReportDefinitionFileLoadException(val file: Path, cause: Throwable) : IllegalArgumentException(
    "Failed to load report definition $file: ${cause.message ?: cause::class.simpleName}",
    cause
)

class DirectoryReportDefinitionLoader(
    private val parser: ReportDefinitionParser,
    private val definitions: ReportDefinitionRegistry,
    private val generators: ReportGeneratorRegistry
) {
    fun load(directory: Path): ReportDefinitionDirectoryLoadResult {
        val normalized = directory.toAbsolutePath().normalize()
        requireDirectory(normalized)
        val files = reportFiles(normalized)
        val parsed = files.map(::parse)
        parsed.forEach(generators::require)
        definitions.registerAll(parsed)
        return ReportDefinitionDirectoryLoadResult(normalized, files, parsed.map { it.id })
    }

    private fun requireDirectory(directory: Path) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw ReportDefinitionDirectoryException("Report definition directory does not exist: $directory")
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw ReportDefinitionDirectoryException("Report definition path is not a directory: $directory")
        }
    }

    private fun reportFiles(directory: Path): List<Path> = try {
        Files.list(directory).use { paths ->
            paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .filter { it.fileName.toString().endsWith(FILE_SUFFIX) }
                .sorted(compareBy { it.fileName.toString() })
                .collect(Collectors.toList())
        }
    } catch (failure: Exception) {
        throw ReportDefinitionDirectoryException("Failed to list report definition directory $directory: ${failure.message}")
    }

    private fun parse(file: Path): ReportDefinition = try {
        parser.parse(Files.readString(file, StandardCharsets.UTF_8).removePrefix(UTF_8_BOM))
    } catch (failure: Exception) {
        throw ReportDefinitionFileLoadException(file, failure)
    }

    companion object {
        const val FILE_SUFFIX = ".report.json"
        private const val UTF_8_BOM = "\uFEFF"
    }
}
