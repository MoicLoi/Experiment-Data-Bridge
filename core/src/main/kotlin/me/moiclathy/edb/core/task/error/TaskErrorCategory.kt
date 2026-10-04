package me.moiclathy.edb.core.task.error

enum class TaskErrorCategory {
    VALIDATION,
    CONFIGURATION,
    AUTHORIZATION,
    NOT_FOUND,
    TRANSIENT,
    TIMEOUT,
    EXECUTION,
    INTERNAL
}
