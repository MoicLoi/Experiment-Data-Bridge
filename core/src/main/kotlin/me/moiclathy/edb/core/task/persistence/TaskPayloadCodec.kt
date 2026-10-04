package me.moiclathy.edb.core.task.persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object TaskPayloadCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(values: Map<String, String>): String = json.encodeToString(values)

    fun decode(value: String?): Map<String, String> =
        if (value.isNullOrBlank()) emptyMap() else json.decodeFromString(value)
}
