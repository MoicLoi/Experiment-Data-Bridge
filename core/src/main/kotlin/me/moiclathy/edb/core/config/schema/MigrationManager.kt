package me.moiclathy.edb.core.config.schema

import me.moiclathy.edb.core.config.migration.ConfigMigration

class MigrationManager(

    private val migrations: List<ConfigMigration>

) {

    fun migrate(

        version: Int,

        config: MutableMap<String, Any?>

    ): MutableMap<String, Any?> {

        var current = version

        while (current < ConfigSchema.CURRENT_VERSION) {

            val migration =

                migrations.first {

                    it.fromVersion == current

                }

            migration.migrate(config)

            current = migration.toVersion
        }

        return config
    }

}