package me.moiclathy.edb.core.config.runtime

import me.moiclathy.edb.core.config.ProjectConfig
import me.moiclathy.edb.core.config.mapper.ProjectConfigMapper
import me.moiclathy.edb.core.config.schema.ConfigSchemaValidator
import me.moiclathy.edb.core.config.schema.ConfigValidationException
import me.moiclathy.edb.core.database.repository.ProjectRepository

class DatabaseProjectConfigLoader(

    private val repository: ProjectRepository,

    private val mapper: ProjectConfigMapper,

    private val validator: ConfigSchemaValidator

) : ProjectConfigLoader {

    override fun load(

        projectId: Int

    ): ProjectConfig {

        val yaml = repository.findConfigYaml(projectId)
            ?: error("Project $projectId config not found")

        val config = mapper.fromYaml(yaml)

        val result = validator.validate(config)
        if (!result.valid) {
            /* 收集错误信息并抛出异常，或者根据你的业务需要进行处理
            val errorMessages = result.errors.joinToString("; ") { "${it.field}: ${it.message}" }
            error("Project $projectId configuration validation failed: $errorMessages")*/
            throw ConfigValidationException(

                result.errors

            )
        }

        return config

    }

}