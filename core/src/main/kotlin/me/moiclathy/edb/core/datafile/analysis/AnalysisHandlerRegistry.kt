package me.moiclathy.edb.core.datafile.analysis

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import java.util.concurrent.ConcurrentHashMap

class AnalysisHandlerNotFoundException(id: String) :
    IllegalArgumentException("Analysis handler is not registered: $id")

class AnalysisHandlerRegistry(
    handlers: Iterable<AnalysisHandler> = emptyList()
) {
    private val handlersById = ConcurrentHashMap<String, AnalysisHandler>()

    init {
        handlers.forEach(::register)
    }

    fun register(handler: AnalysisHandler) {
        require(ID.matches(handler.id)) { "Analysis handler id is invalid: ${handler.id}" }
        require(handler.analysisType.isNotBlank()) { "Analysis type must not be blank." }
        require(handler.tool.isNotBlank()) { "Analysis tool must not be blank." }
        require(handler.outputFormat != DataFileFormat.UNKNOWN) { "Analysis output format must be known." }
        val previous = handlersById.putIfAbsent(handler.id, handler)
        require(previous == null) { "Analysis handler is already registered: ${handler.id}" }
    }

    fun find(id: String): AnalysisHandler? = handlersById[id]

    fun require(id: String): AnalysisHandler = find(id) ?: throw AnalysisHandlerNotFoundException(id)

    fun handlers(): List<AnalysisHandler> = handlersById.values.sortedBy { it.id }

    companion object {
        private val ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")
    }
}
