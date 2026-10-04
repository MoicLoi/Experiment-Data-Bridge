package me.moiclathy.edb.core.task.retention

import me.moiclathy.edb.core.exception.EDBException

class TaskRetentionAlreadyRunningException : EDBException(
    code = "TASK_RETENTION_ALREADY_RUNNING",
    message = "Task retention cleanup is already running"
)
