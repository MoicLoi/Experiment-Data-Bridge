package me.moiclathy.edb.core.service

import me.moiclathy.edb.core.config.mapper.SnakeYamlProjectConfigMapper
import me.moiclathy.edb.core.config.schema.ConfigSchemaValidator
import me.moiclathy.edb.core.config.schema.ConfigValidationError
import me.moiclathy.edb.core.config.validation.ConfigurationValidationReport

class ProjectConfigurationValidationService(

    private val validator: ConfigSchemaValidator,

    private val mapper: SnakeYamlProjectConfigMapper

) {


    fun validate(

        yamlContent: String

    ): ConfigurationValidationReport {


        return try {

            val config = mapper.fromYaml(
                yamlContent
            )


            val result =
                validator.validate(
                    config
                )


            ConfigurationValidationReport(

                valid = result.valid,

                errors = result.errors

            )


        } catch (ex: Exception) {


            ConfigurationValidationReport(

                valid = false,

                errors = listOf(

                    // 如果你的 ConfigValidationError 构造不同，
                    // 根据你的定义调整
                    ConfigValidationError(
                        field = "yaml",
                        message = ex.message
                            ?: "Invalid configuration"
                    )

                )

            )

        }

    }

}