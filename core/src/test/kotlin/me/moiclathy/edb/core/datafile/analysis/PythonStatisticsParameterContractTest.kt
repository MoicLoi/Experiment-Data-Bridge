package me.moiclathy.edb.core.datafile.analysis

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import java.net.URI
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PythonStatisticsParameterContractTest {
    private val handler = PythonStatisticsAnalysisHandler(
        id = "python-statistics",
        analysisType = "tabular-statistics",
        endpoint = URI.create("http://127.0.0.1:8000/analyze"),
        outputFormat = DataFileFormat.JSON,
        bearerToken = null,
        connectTimeout = Duration.ofSeconds(1),
        requestTimeout = Duration.ofSeconds(5)
    )

    @Test
    fun `guided values encode existing worker protocol`() {
        val analysis = JsonObject(mapOf(
            "name" to JsonPrimitive("Weight comparison"),
            "method" to JsonPrimitive("welch_t_test"),
            "groupColumn" to JsonPrimitive("group"),
            "groupA" to JsonPrimitive("control"),
            "groupB" to JsonPrimitive("treated"),
            "valueColumn" to JsonPrimitive("weight")
        ))

        val encoded = handler.encodeParameterValues(JsonObject(mapOf(
            "outputFileName" to JsonPrimitive("result.json"),
            "analyses" to JsonArray(listOf(analysis))
        )))

        assertEquals("tabular-statistics", encoded["operation"])
        assertEquals("result.json", encoded["outputFileName"])
        val spec = Json.parseToJsonElement(requireNotNull(encoded["spec"])) as JsonObject
        assertEquals(JsonPrimitive(1), spec["schemaVersion"])
        assertEquals(analysis, (spec["analyses"] as JsonArray).single())
    }

    @Test
    fun `empty analysis list is rejected`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            handler.encodeParameterValues(JsonObject(mapOf("analyses" to JsonArray(emptyList()))))
        }
        assertTrue(failure.message.orEmpty().contains("fewer than 1"))
    }
}
