package me.moiclathy.edb.core.task.cancellation

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.repository.TaskRepository
import java.time.Clock
import java.time.Instant

class TaskCancellationService(
    private val repository: TaskRepository,
    private val clock: Clock
) {
    fun requestCancellation(taskId: Int, requestedBy: Int): Task =
        repository.requestCancellation(
            taskId = taskId,
            requestedBy = requestedBy,
            at = Instant.now(clock)
        )
}
