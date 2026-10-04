package me.moiclathy.edb.core.config.migration

class MigrationV1ToV2 : ConfigMigration {

    override val fromVersion = 1

    override val toVersion = 2

    override fun migrate(
        config: MutableMap<String, Any?>
    ) {

        // 1. 更新版本号字段
        config["version"] = 2

        // 2. 示例：如果后续有从 v1 到 v2 的具体字段演进逻辑，可在此安全操作
        // 例如：将旧字段重命名或赋予默认值
        // if (config.containsKey("oldField")) {
        //     config["newField"] = config.remove("oldField")
        // }

    }

}