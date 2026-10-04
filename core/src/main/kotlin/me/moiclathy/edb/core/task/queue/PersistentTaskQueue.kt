package me.moiclathy.edb.core.task.queue

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.repository.TaskRepository
import java.time.Instant

class PersistentTaskQueue(
    private val repository: TaskRepository
) : TaskQueue {

    override fun enqueue(task: Task, at: Instant): Task {
        val queued = task
            .transitionTo(TaskStatus.QUEUED, at)
            .copy(nextAttemptAt = null)
        return if (queued.id == null) {
            repository.save(queued)
        } else {
            repository.update(queued)
        }
    }

    override fun peek(limit: Int): List<Task> = repository.findQueued(limit)

    override fun claimNext(at: Instant): Task? = repository.claimNextQueued(at)
}
