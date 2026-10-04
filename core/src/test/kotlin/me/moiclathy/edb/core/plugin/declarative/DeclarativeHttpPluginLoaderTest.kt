package me.moiclathy.edb.core.plugin.declarative

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DeclarativeHttpPluginLoaderTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `loads a strict analysis capability with schema and env secret`() {
        val plugin = directory.resolve("sample")
        Files.createDirectories(plugin)
        Files.writeString(plugin.resolve("parameters.schema.json"), """{"type":"object","properties":{"alpha":{"type":"number"}}}""")
        Files.writeString(plugin.resolve("plugin.json"), descriptor("http://localhost:8787", """, "auth":{"type":"BEARER","secretRef":"env:EDB_TEST_TOKEN"}"""))

        val result = DeclarativeHttpPluginLoader(setOf("localhost"), environment = { if (it == "EDB_TEST_TOKEN") "secret" else null }).load(directory)

        assertTrue(result.failures.isEmpty()) { result.failures.joinToString { it.message } }
        assertEquals(listOf("sample.statistics"), result.handlers.map { it.id })
        val handler = result.handlers.single()
        assertEquals("sample-adapter", handler.capabilityMetadata?.sourceId)
        assertEquals("1.0.0", handler.capabilityMetadata?.sourceVersion)
        assertEquals(listOf("analysis", "sample"), handler.capabilityMetadata?.cliPath)
        assertEquals("plugin.sample.statistics.title", handler.capabilityMetadata?.titleKey)
        assertEquals(listOf("sample-analysis"), handler.capabilityMetadata?.phrasePatterns?.map { it.id })
        assertEquals(mapOf("alpha" to "0.05"), handler.encodeParameterValues(buildJsonObject { put("alpha", 0.05) }))
    }

    @Test
    fun `isolates descriptors that target a host outside the allowlist`() {
        val plugin = directory.resolve("unsafe")
        Files.createDirectories(plugin)
        Files.writeString(plugin.resolve("parameters.schema.json"), """{"type":"object"}""")
        Files.writeString(plugin.resolve("plugin.json"), descriptor("https://example.com"))

        val result = DeclarativeHttpPluginLoader(setOf("localhost")).load(directory)

        assertTrue(result.loaded.isEmpty())
        assertTrue(result.failures.single().message.contains("not allowed")) { result.failures.single().message }
    }

    @Test
    fun `rejects unknown contract fields`() {
        val plugin = directory.resolve("unknown")
        Files.createDirectories(plugin)
        Files.writeString(plugin.resolve("parameters.schema.json"), """{"type":"object"}""")
        Files.writeString(plugin.resolve("plugin.json"), descriptor("http://localhost:8787").replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"unexpected\":true"))

        val result = DeclarativeHttpPluginLoader(setOf("localhost")).load(directory)

        assertTrue(result.loaded.isEmpty())
        assertEquals(1, result.failures.size)
    }

    private fun descriptor(baseUrl: String, authFragment: String = ""): String = """
        {
          "schemaVersion":1,
          "plugin":{"id":"sample-adapter","version":"1.0.0"},
          "connection":{"baseUrl":"$baseUrl"$authFragment},
          "capabilities":[{
            "id":"sample.statistics",
            "type":"ANALYSIS",
            "titleKey":"plugin.sample.statistics.title",
            "request":{"path":"/v1/analyze"},
            "inputFormats":["CSV","XLSX"],
            "outputFormat":"JSON",
            "parameterSchema":"parameters.schema.json",
            "cli":{"path":["analysis","sample"]},
            "phrasePatterns":[{"id":"sample-analysis","tokens":[
              {"type":"LITERAL","i18nKey":"phrase.execute"},
              {"type":"ENUM","name":"handlerId","source":"analysis.handlers"},
              {"type":"PARAMETER","name":"alpha","optional":true}
            ]}]
          }]
        }
    """.trimIndent()
}
