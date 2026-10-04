package me.moiclathy.edb.core.config.validation

import me.moiclathy.edb.core.config.schema.ConfigValidationError

data class ConfigurationValidationReport(

    val valid: Boolean,

    val errors: List<ConfigValidationError>

)