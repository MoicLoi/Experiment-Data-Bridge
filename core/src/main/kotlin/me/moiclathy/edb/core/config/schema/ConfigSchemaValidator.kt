package me.moiclathy.edb.core.config.schema

import me.moiclathy.edb.core.config.ProjectConfig

class ConfigSchemaValidator(
    private val schemaRegistry: ConfigSchemaRegistry
) {

    fun validate(
        config: ProjectConfig
    ): ConfigValidationResult {
        val errors = mutableListOf<ConfigValidationError>()

        // 1. 全局版本号范围校验
        if (
            config.version < ConfigSchema.MIN_SUPPORTED_VERSION ||
            config.version > ConfigSchema.CURRENT_VERSION
        ) {
            errors += ConfigValidationError(
                field = "version",
                message = "Unsupported configuration version ${config.version}"
            )
            // 如果版本本身就不支持，直接返回，避免后续从 Registry 找不到 Schema 抛出异常
            return ConfigValidationResult(valid = false, errors = errors)
        }

        // 2. 通过 Registry 获取对应版本的 Schema 定义
        val schema = schemaRegistry.schema(config.version)
        if (schema == null) {
            errors += ConfigValidationError(
                field = "version",
                message = "No schema definition found for version ${config.version}"
            )
            return ConfigValidationResult(valid = false, errors = errors)
        }

        // 3. 分发给具体的 Schema 规则执行校验
        errors += schema.validate(config)

        // 4. 返回最终结果
        return if (errors.isEmpty()) {
            ConfigValidationResult.success()
        } else {
            ConfigValidationResult(
                valid = false,
                errors = errors
            )
        }
    }
}