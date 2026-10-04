package me.moiclathy.edb.core.datafile.analysis

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PythonHttpAnalysisParameterContractTest {
    private val handler = PythonHttpAnalysisHandler(
        PythonHttpAnalysisHandlerConfig(
            endpoint = URI.create("http://127.0.0.1:8000/analyze"),
            outputFormat = DataFileFormat.JSON
        )
    )

    @Test
    fun `encodes structured tabular values into the existing worker protocol`() {
        val encoded = handler.encodeParameterValues(buildJsonObject {
            put("outputFormat", "CSV")
            put("outputFileName", "cleaned.csv")
            put("trimStrings", true)
            put("normalizeCase", "upper")
            put("normalizeColumns", "subject, group")
            put("dropMissingColumns", "measurement_mm")
            put("duplicateKeyColumns", "sample_id")
            put("numericColumn", "measurement_mm")
            put("numericMin", 0)
            put("numericMax", 25)
        })

        assertEquals("tabular-clean", encoded["operation"])
        val spec = Json.parseToJsonElement(encoded.getValue("spec")).jsonObject
        assertEquals("CSV", spec.getValue("output").jsonObject.getValue("format").jsonPrimitive.content)
        assertEquals("cleaned.csv", spec.getValue("output").jsonObject.getValue("fileName").jsonPrimitive.content)
        assertEquals(
            listOf("trimStrings", "normalizeCase", "dropRowsWithMissing", "removeDuplicates", "numericRange"),
            spec.getValue("rules").jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content }
        )
    }

    @Test
    fun `rejects unknown structured values and invalid ranges`() {
        assertFailsWith<IllegalArgumentException> {
            handler.encodeParameterValues(buildJsonObject { put("executePython", "dangerous") })
        }
        assertFailsWith<IllegalArgumentException> {
            handler.encodeParameterValues(buildJsonObject { put("numericMin", 10); put("numericMax", 1) })
        }
    }
}
