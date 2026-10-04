package me.moiclathy.edb.core.config.merge.exception

/**
 * Snapshot 合并异常。
 */
class SnapshotMergeException(
    code: String,
    message: String,
    cause: Throwable? = null
) : ConfigurationMergeException(
    code = code,
    message = message,
    cause = cause
)