package me.moiclathy.edb.plugin.api

object EdbPluginApi {
    const val VERSION = "1.1"
}

data class PluginDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: String,
    val vendor: String? = null,
    val description: String? = null
) {
    init {
        require(PLUGIN_ID.matches(id)) { "Plugin id is invalid: $id" }
        require(name.isNotBlank()) { "Plugin name must not be blank." }
        require(SEMANTIC_VERSION.matches(version)) { "Plugin version must use semantic versioning: $version" }
        require(API_VERSION.matches(apiVersion)) { "Plugin API version must use major.minor format: $apiVersion" }
        require(vendor == null || vendor.isNotBlank()) { "Plugin vendor must not be blank." }
        require(description == null || description.isNotBlank()) { "Plugin description must not be blank." }
    }

    companion object {
        private val PLUGIN_ID = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")
        private val SEMANTIC_VERSION = Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)(?:[-+][0-9A-Za-z.-]+)?")
        private val API_VERSION = Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)")
    }
}
