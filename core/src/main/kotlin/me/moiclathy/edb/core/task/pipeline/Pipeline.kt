package me.moiclathy.edb.core.task.pipeline

/** Immutable data passed from one pipeline step to the next. */
class PipelineContext(
    values: Map<String, Any?> = emptyMap()
) {
    private val values = values.toMap()

    init {
        require(this.values.keys.none { it.isBlank() }) {
            "Pipeline context keys must not be blank."
        }
    }

    operator fun get(key: String): Any? = values[key]

    inline fun <reified T> getAs(key: String): T? = get(key) as? T

    fun contains(key: String): Boolean = values.containsKey(key)

    fun withValue(key: String, value: Any?): PipelineContext {
        require(key.isNotBlank()) { "Pipeline context key must not be blank." }
        return PipelineContext(values + (key to value))
    }

    fun withValues(newValues: Map<String, Any?>): PipelineContext =
        PipelineContext(values + newValues)

    fun asMap(): Map<String, Any?> = values.toMap()

    companion object {
        val EMPTY = PipelineContext()
    }
}

sealed interface PipelineStepResult {
    data class Success(val context: PipelineContext) : PipelineStepResult

    data class Failure(
        val code: String,
        val message: String,
        val retryable: Boolean = false
    ) : PipelineStepResult {
        init {
            require(code.isNotBlank()) { "Pipeline failure code must not be blank." }
            require(message.isNotBlank()) { "Pipeline failure message must not be blank." }
        }
    }
}

class PipelineStep(
    val id: String,
    private val action: suspend (PipelineContext) -> PipelineStepResult
) {
    init {
        require(id.isNotBlank()) { "Pipeline step id must not be blank." }
    }

    suspend fun execute(context: PipelineContext): PipelineStepResult = action(context)
}

data class Pipeline(
    val id: String,
    val steps: List<PipelineStep>
) {
    init {
        require(id.isNotBlank()) { "Pipeline id must not be blank." }
        require(steps.isNotEmpty()) { "Pipeline must contain at least one step." }
        require(steps.map { it.id }.distinct().size == steps.size) {
            "Pipeline step ids must be unique."
        }
    }
}

object PipelineSystemContextKeys {
    const val TASK_ID = "system.taskId"
    const val PROJECT_ID = "system.projectId"
    const val TRIGGER_USER_ID = "system.triggerUserId"
}
