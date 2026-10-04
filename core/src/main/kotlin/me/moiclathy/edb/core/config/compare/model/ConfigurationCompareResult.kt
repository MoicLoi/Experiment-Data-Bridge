package me.moiclathy.edb.core.config.compare.model

import me.moiclathy.edb.core.config.snapshot.model.ConfigurationSnapshot

data class ConfigurationCompareResult(

    val sourceSnapshot: ConfigurationSnapshot,

    val targetSnapshot: ConfigurationSnapshot,

    val changes: List<ConfigurationCompareChange>
) {

    val isIdentical: Boolean
        get() = changes.isEmpty()

    val added: List<ConfigurationCompareChange>
        get() = changes.filter {
            it.type == ConfigurationCompareChangeType.ADDED
        }

    val removed: List<ConfigurationCompareChange>
        get() = changes.filter {
            it.type == ConfigurationCompareChangeType.REMOVED
        }

    val changed: List<ConfigurationCompareChange>
        get() = changes.filter {
            it.type == ConfigurationCompareChangeType.CHANGED
        }
}