package me.moiclathy.edb.core.config.migration

interface ConfigMigration {

    val fromVersion: Int

    val toVersion: Int

    fun migrate(
        config: MutableMap<String, Any?>
    )

}