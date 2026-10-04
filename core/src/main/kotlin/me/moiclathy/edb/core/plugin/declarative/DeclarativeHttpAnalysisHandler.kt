package me.moiclathy.edb.core.plugin.declarative

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.moiclathy.edb.core.datafile.analysis.AnalysisParameterContract
import me.moiclathy.edb.core.datafile.analysis.AnalysisCapabilityMetadata
import me.moiclathy.edb.core.datafile.analysis.ExternalHttpAnalysisHandler
import me.moiclathy.edb.core.datafile.analysis.ExternalHttpAnalysisHandlerConfig

class DeclarativeHttpAnalysisHandler(
    config: ExternalHttpAnalysisHandlerConfig,
    override val parameterContract: AnalysisParameterContract,
    override val capabilityMetadata: AnalysisCapabilityMetadata,
    json: Json
) : ExternalHttpAnalysisHandler(config, json) {
    override fun encodeParameterValues(values: JsonObject): Map<String, String> = values.mapValues { (_, value) ->
        if (value is JsonPrimitive && value.isString) value.contentOrNull.orEmpty() else value.toString()
    }
}
