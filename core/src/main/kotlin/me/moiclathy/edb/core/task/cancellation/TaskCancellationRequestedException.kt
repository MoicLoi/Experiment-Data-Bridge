package me.moiclathy.edb.core.task.cancellation

import kotlinx.coroutines.CancellationException

class TaskCancellationRequestedException(
    taskId: Int
) : CancellationException("Cancellation requested for task: $taskId")
