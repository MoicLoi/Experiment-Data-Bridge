package me.moiclathy.edb.core.datafile.report

import java.util.concurrent.ConcurrentHashMap

class ReportGeneratorNotFoundException(id: String) :
    IllegalArgumentException("Report generator is not registered: $id")

class ReportGeneratorRegistry(
    generators: Iterable<ReportGenerator> = emptyList()
) {
    private val generatorsById = ConcurrentHashMap<String, ReportGenerator>()

    init {
        generators.forEach(::register)
    }

    fun register(generator: ReportGenerator) {
        require(generator.id.isNotBlank()) { "Report generator id must not be blank." }
        require(generator.supportedFormats.isNotEmpty()) {
            "Report generator must support at least one output format."
        }
        val previous = generatorsById.putIfAbsent(generator.id, generator)
        require(previous == null) { "Report generator is already registered: ${generator.id}" }
    }

    fun find(id: String): ReportGenerator? = generatorsById[id]

    fun require(definition: ReportDefinition): ReportGenerator {
        val generator = find(definition.generatorId)
            ?: throw ReportGeneratorNotFoundException(definition.generatorId)
        require(definition.outputFormat in generator.supportedFormats) {
            "Report generator ${generator.id} does not support ${definition.outputFormat}."
        }
        return generator
    }

    fun ids(): Set<String> = generatorsById.keys.toSet()
}
