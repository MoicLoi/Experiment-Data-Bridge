package me.moiclathy.edb.core.config.schema

import me.moiclathy.edb.core.exception.EDBException

class ConfigValidationException(

    val errors: List<ConfigValidationError>

) : /*RuntimeException*/EDBException(

    code = "CONFIG_VALIDATION_FAILED",
    message = buildString {
        append("Configuration validation failed")
        errors.forEach {
            append("\n")
            append(it.field)
            append(": ")
            append(it.message)
        }
    }

)