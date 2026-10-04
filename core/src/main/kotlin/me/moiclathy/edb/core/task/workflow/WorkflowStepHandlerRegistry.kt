package me.moiclathy.edb.core.task.workflow

import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import java.util.concurrent.ConcurrentHashMap

interface WorkflowStepHandler {
    val type: String
    val supportedVersions: Set<String> get() = setOf("1")
    val source: WorkflowStepHandlerSource get() = WorkflowStepHandlerSource.BUILT_IN

    suspend fun execute(
        step: WorkflowStepDefinition,
        context: PipelineContext
    ): PipelineStepResult
}

enum class WorkflowStepHandlerSource {
    BUILT_IN,
    PLUGIN
}

class WorkflowStepHandlerNotFoundException(type: String) :
    IllegalArgumentException("Workflow step handler is not registered: $type")

/** Thread-safe whitelist of the step types that JSON workflows may execute. */
class WorkflowStepHandlerRegistry(
    handlers: Iterable<WorkflowStepHandler> = emptyList()
) {
    private val handlersByType = ConcurrentHashMap<String, WorkflowStepHandler>()

    init {
        handlers.forEach(::register)
    }

    fun register(handler: WorkflowStepHandler) {
        require(handler.type.isNotBlank()) { "Workflow step handler type must not be blank." }
        require(handler.type == handler.type.trim()) {
            "Workflow step handler type must not contain leading or trailing whitespace."
        }
        require(handler.supportedVersions.isNotEmpty()) { "Workflow step handler must support at least one version." }
        require(handler.supportedVersions.all { it.isNotBlank() && it == it.trim() }) {
            "Workflow step handler versions must not be blank or contain surrounding whitespace."
        }
        val previous = handlersByType.putIfAbsent(handler.type, handler)
        require(previous == null) { "Workflow step handler already registered: ${handler.type}" }
    }

    fun find(type: String): WorkflowStepHandler? = handlersByType[type]

    fun require(type: String): WorkflowStepHandler = find(type)
        ?: throw WorkflowStepHandlerNotFoundException(type)

    fun require(type: String, version: String): WorkflowStepHandler = require(type).also { handler ->
        if (version !in handler.supportedVersions) {
            throw WorkflowStepHandlerVersionNotSupportedException(type, version, handler.supportedVersions)
        }
    }

    fun registeredTypes(): Set<String> = handlersByType.keys.toSet()

    fun registeredHandlers(): List<WorkflowStepHandler> = handlersByType.values.sortedBy { it.type }
}

class WorkflowStepHandlerVersionNotSupportedException(
    type: String,
    version: String,
    supportedVersions: Set<String>
) : IllegalArgumentException(
    "Workflow step handler $type does not support version $version; supported versions: ${supportedVersions.sorted().joinToString()}"
)
