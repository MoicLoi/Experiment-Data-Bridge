package me.moiclathy.edb.core.datafile.report

import me.moiclathy.edb.core.datafile.model.DataFileFormat

data class ReportDefinition(
    val id: String,
    val version: Int = 1,
    val title: String,
    val generatorId: String,
    val outputFormat: DataFileFormat,
    val outputFileName: String,
    val defaultParameters: Map<String, String> = emptyMap()
) {
    init {
        require(ID.matches(id)) { "Report definition id is invalid: $id" }
        require(version > 0) { "Report definition version must be positive." }
        require(title.isNotBlank()) { "Report title must not be blank." }
        require(ID.matches(generatorId)) { "Report generator id is invalid: $generatorId" }
        require(outputFormat != DataFileFormat.UNKNOWN) { "Report output format must be known." }
        require(outputFileName.isNotBlank()) { "Report output file name must not be blank." }
        require(defaultParameters.keys.none { it.isBlank() }) {
            "Report default parameter names must not be blank."
        }
    }

    companion object {
        private val ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")
    }
}
