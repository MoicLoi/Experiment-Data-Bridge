package me.moiclathy.edb.core.config.schema

class ConfigSchemaRegistry(

    definitions: List<ConfigSchemaDefinition>

) {

    private val schemas =

        definitions.associateBy {

            it.version

        }

    fun schema(

        version: Int

    ): ConfigSchemaDefinition? =

        schemas[version]

}