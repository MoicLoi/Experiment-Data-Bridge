package me.moiclathy.edb.core.config.migration

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

class ConfigVersionReader {

    fun readVersion(
        yaml: String
    ): Int {

        val settings = LoadSettings.builder().build()
        val loader = Load(settings)

        val root = loader.loadFromString(yaml)

        @Suppress("UNCHECKED_CAST")
        val map = root as? Map<String, Any?> ?: emptyMap()

        return (map["version"] as? Number)?.toInt() ?: 1
    }
}