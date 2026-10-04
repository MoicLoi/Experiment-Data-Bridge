package me.moiclathy.edb.core.config.schema

import me.moiclathy.edb.core.config.mapper.SnakeYamlProjectConfigMapper

class YamlValidationService(

    private val mapper: SnakeYamlProjectConfigMapper,

    private val validator: ConfigSchemaValidator

) {

    fun validate(

        yaml:String

    ) {

        val config =

            mapper.fromYaml(yaml)

        val result =

            validator.validate(config)

        if (!result.valid) {

            throw ConfigValidationException(

                result.errors

            )

        }

    }

}
//TODO以后：
//
//Import
//Restore
//Save
//CLI
//Docker 初始化
//
//全部复用这一份。