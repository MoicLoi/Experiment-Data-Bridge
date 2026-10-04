package me.moiclathy.edb.core.config.migration

import me.moiclathy.edb.core.config.schema.ConfigSchema
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

class ConfigMigrationManager(
    private val migrations: List<ConfigMigration>
) {
    // 使用 SnakeYAML Engine 的默认设置（或者与你项目中 SnakeYamlProjectConfigMapper 一致的设置）
    private val load = Load(LoadSettings.builder().build())
    private val dump = Dump(DumpSettings.builder().build())

    @Suppress("UNCHECKED_CAST")
    fun migrate(
        yaml: String,
        version: Int
    ): String {
        var currentVersion = version

        // 1. 使用 SnakeYAML Engine 将 YAML 字符串解析为 MutableMap
        val loadedObject = load.loadFromString(yaml)
        val currentMap = (loadedObject as? MutableMap<String, Any?>)
            ?: mutableMapOf() // 防止空文件或非 Map 结构的兜底

        while (true) {
            val migration = migrations.firstOrNull {
                it.fromVersion == currentVersion
            } ?: break

            // 🛡️ 保护 1：防止迁移方向错误或死循环（要求目标版本必须大于起始版本）
            require(migration.toVersion > migration.fromVersion) {
                "Invalid migration ${migration.fromVersion} -> ${migration.toVersion}"
            }

            // 2. 执行结构化迁移
            migration.migrate(currentMap)

            // 🛡️ 保护 2：防止 Migration 中漏写版本号更新
            val migratedVersion = (currentMap["version"] as? Number)?.toInt()
                ?: throw IllegalStateException("Migration from ${migration.fromVersion} did not produce a version field.")

            check(migratedVersion == migration.toVersion) {
                "Migration did not update version field correctly. Expected target version ${migration.toVersion}, but got $migratedVersion"
            }

            currentVersion = migration.toVersion
        }

        // 3. 使用 SnakeYAML Engine 将迁移后的 MutableMap  dump 回 YAML 字符串
        return dump.dumpToString(currentMap)
    }

    fun hasMigration(
        version: Int
    ): Boolean {

        return migrations.any {

            it.fromVersion == version

        }

    }

    fun latestVersion(): Int {

        return migrations

            .maxOfOrNull {

                it.toVersion

            }

            ?: ConfigSchema.CURRENT_VERSION

    }

    fun canUpgrade(

        version:Int

    ):Boolean{

        return version < latestVersion()

    }
}