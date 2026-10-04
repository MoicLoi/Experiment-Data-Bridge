package me.moiclathy.edb.core.task.execution

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.result.TaskResult

/** Executes one supported task type. */
interface TaskHandler {
    val taskType: String

    suspend fun execute(task: Task, context: TaskExecutionContext): TaskResult?
}
