package me.moiclathy.edb.plugin.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginDescriptorTest {
    @Test
    fun `accepts stable plugin identity and semantic version`() {
        val descriptor = PluginDescriptor(
            id = "deeplabcut.http",
            name = "DeepLabCut HTTP adapter",
            version = "1.2.0",
            apiVersion = EdbPluginApi.VERSION
        )

        assertEquals("deeplabcut.http", descriptor.id)
    }

    @Test
    fun `rejects invalid plugin id and version`() {
        assertFailsWith<IllegalArgumentException> {
            PluginDescriptor("Invalid ID", "Invalid", "1", "1.0")
        }
        assertFailsWith<IllegalArgumentException> {
            PluginDescriptor("valid.id", "Invalid", "1.0", "1.0")
        }
    }
}
