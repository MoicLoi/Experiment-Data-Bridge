package me.moiclathy.edb.core.exception

import me.moiclathy.edb.core.config.schema.ConfigValidationError


class ConfigurationValidationException(

    val errors: List<ConfigValidationError>

):
    RuntimeException(
        "Configuration validation failed"
    )