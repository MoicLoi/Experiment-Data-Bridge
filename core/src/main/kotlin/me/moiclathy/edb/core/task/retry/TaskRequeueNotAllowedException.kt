package me.moiclathy.edb.core.task.retry

import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.core.task.TaskStatus

class TaskRequeueNotAllowedException(taskId: Int, status: TaskStatus) : EDBException(
    code = "TASK_NOT_RETRYABLE",
    message = "Task $taskId cannot be requeued from status $status"
)
