package me.moiclathy.edb.core.task.pipeline

import java.util.concurrent.ConcurrentHashMap

/** Thread-safe registry of executable pipeline definitions. */
class PipelineRegistry(
    pipelines: Iterable<Pipeline> = emptyList()
) {
    private val pipelinesById = ConcurrentHashMap<String, Pipeline>()

    init {
        pipelines.forEach(::register)
    }

    fun register(pipeline: Pipeline) {
        val previous = pipelinesById.putIfAbsent(pipeline.id, pipeline)
        if (previous != null) throw PipelineAlreadyRegisteredException(pipeline.id)
    }

    fun find(pipelineId: String): Pipeline? = pipelinesById[pipelineId.trim()]

    fun registeredIds(): Set<String> = pipelinesById.keys.toSet()
}

class PipelineAlreadyRegisteredException(val pipelineId: String) :
    IllegalArgumentException("Pipeline already registered: $pipelineId")
