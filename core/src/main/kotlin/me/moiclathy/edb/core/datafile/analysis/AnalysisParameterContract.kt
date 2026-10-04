package me.moiclathy.edb.core.datafile.analysis

import kotlinx.serialization.json.JsonObject

data class AnalysisParameterContract(
    val id: String,
    val version: Int,
    val title: String,
    val description: String? = null,
    val schema: JsonObject
) {
    init {
        require(id.isNotBlank()) { "Analysis parameter contract id must not be blank." }
        require(version > 0) { "Analysis parameter contract version must be positive." }
        require(title.isNotBlank()) { "Analysis parameter contract title must not be blank." }
    }
}

fun interface AnalysisParameterEncoder {
    fun encode(values: JsonObject): Map<String, String>
}
