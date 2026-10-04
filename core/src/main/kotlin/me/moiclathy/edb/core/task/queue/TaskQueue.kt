package me.moiclathy.edb.core.task.queue

import me.moiclathy.edb.core.task.Task
import java.time.Instant

interface TaskQueue {
    fun enqueue(task: Task, at: Instant): Task

    fun peek(limit: Int = 100): List<Task>

    fun claimNext(at: Instant): Task?
}
