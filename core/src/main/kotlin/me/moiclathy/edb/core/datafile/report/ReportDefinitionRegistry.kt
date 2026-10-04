package me.moiclathy.edb.core.datafile.report

import java.util.concurrent.ConcurrentHashMap

class ReportDefinitionNotFoundException(id: String) :
    IllegalArgumentException("Report definition is not registered: $id")

class ReportDefinitionRegistry(
    definitions: Iterable<ReportDefinition> = emptyList()
) {
    private val definitionsById = ConcurrentHashMap<String, ReportDefinition>()

    init {
        registerAll(definitions)
    }

    fun register(definition: ReportDefinition) = registerAll(listOf(definition))

    @Synchronized
    fun registerAll(definitions: Iterable<ReportDefinition>) {
        val incoming = definitions.toList()
        val duplicateIds = incoming.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        val conflicts = duplicateIds + incoming.map { it.id }.intersect(definitionsById.keys)
        require(conflicts.isEmpty()) {
            "Report definitions are duplicated or already registered: ${conflicts.sorted().joinToString()}"
        }
        incoming.forEach { definitionsById[it.id] = it }
    }

    fun find(id: String): ReportDefinition? = definitionsById[id]

    fun require(id: String): ReportDefinition = find(id)
        ?: throw ReportDefinitionNotFoundException(id)

    fun definitions(): List<ReportDefinition> = definitionsById.values.sortedBy { it.id }
}
