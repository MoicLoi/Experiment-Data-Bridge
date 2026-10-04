package me.moiclathy.edb.core.config.merge.exception

import me.moiclathy.edb.core.exception.EDBException

open class ConfigurationMergeException(
    code: String,
    message: String,
    cause: Throwable? = null
) : EDBException(
    code = code,
    message = message,
)