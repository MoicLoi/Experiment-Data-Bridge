package me.moiclathy.edb.core.task.execution

import me.moiclathy.edb.core.task.Task

interface TaskExecutor {
    suspend fun execute(task: Task): Task
}
