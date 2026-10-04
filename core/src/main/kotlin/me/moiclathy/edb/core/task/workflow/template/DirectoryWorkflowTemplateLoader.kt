package me.moiclathy.edb.core.task.workflow.template

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

data class WorkflowTemplateLoadResult(val directory: Path, val templateIds: List<String>)

class DirectoryWorkflowTemplateLoader(private val codec: JsonWorkflowTemplateCodec, private val registry: WorkflowTemplateRegistry) {
    fun load(directory: Path): WorkflowTemplateLoadResult {
        if (!Files.exists(directory)) Files.createDirectories(directory)
        require(Files.isDirectory(directory)) { "Workflow template path is not a directory: $directory." }
        val loaded = Files.list(directory).use { paths -> paths
            .filter { Files.isRegularFile(it) && it.extension.equals("json", true) && it.name.endsWith(".workflow-template.json") }
            .sorted().map { path -> codec.deserialize(Files.readString(path)).also(registry::register) }.toList() }
        return WorkflowTemplateLoadResult(directory.toAbsolutePath().normalize(), loaded.map { "${it.id}@${it.version}" })
    }
}
