package me.moiclathy.edb.core.config.schema

data class ConfigValidationError(

    val field: String,

    val message: String

)