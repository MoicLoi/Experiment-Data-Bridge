package me.moiclathy.edb.core.task.progress

import me.moiclathy.edb.core.task.repository.TaskRepository
import java.time.Clock
import java.time.Instant

class TaskProgressService(
    private val repository: TaskRepository,
    private val clock: Clock
) {
    fun get(taskId: Int): TaskProgress? =
        repository.findById(taskId)?.progress

    fun update(taskId: Int, percent: Int, message: String?): TaskProgress {
        val progress = TaskProgress(
            percent = percent,
            message = message,
            updatedAt = Instant.now(clock)
        )
        return requireNotNull(repository.updateProgress(taskId, progress).progress)
    }
}
