package me.moiclathy.edb.core.task.error

/** Exception carrying the structured error contract expected by [TaskError]. */
class TaskExecutionException(
    val code: String,
    val category: TaskErrorCategory,
    override val message: String,
    val retryable: Boolean = false,
    val details: Map<String, String> = emptyMap(),
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    init {
        require(code.isNotBlank()) { "Task execution error code must not be blank." }
        require(message.isNotBlank()) { "Task execution error message must not be blank." }
    }
}
