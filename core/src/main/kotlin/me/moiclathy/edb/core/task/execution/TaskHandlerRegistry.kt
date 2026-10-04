package me.moiclathy.edb.core.task.execution

import java.util.concurrent.ConcurrentHashMap

class TaskHandlerRegistry(
    handlers: Iterable<TaskHandler> = emptyList()
) {
    private val handlersByType = ConcurrentHashMap<String, TaskHandler>()

    init {
        handlers.forEach(::register)
    }

    fun register(handler: TaskHandler) {
        val taskType = handler.taskType.trim()
        require(taskType.isNotEmpty()) { "Task handler type must not be blank." }

        val previous = handlersByType.putIfAbsent(taskType, handler)
        require(previous == null) { "Task handler already registered: $taskType" }
    }

    fun find(taskType: String): TaskHandler? = handlersByType[taskType.trim()]

    fun registeredTypes(): Set<String> = handlersByType.keys.toSet()
}
