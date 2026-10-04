package me.moiclathy.edb.core.task.application

import me.moiclathy.edb.core.exception.EDBException

class TaskNotFoundException(taskId: Int) : EDBException(
    code = "TASK_NOT_FOUND",
    message = "Task $taskId not found"
)

class PipelineNotFoundException(pipelineId: String) : EDBException(
    code = "PIPELINE_NOT_FOUND",
    message = "Pipeline $pipelineId not found"
)
