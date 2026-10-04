package me.moiclathy.edb.core.task.error

import java.io.IOException
import java.time.Instant

class DefaultTaskErrorClassifier : TaskErrorClassifier {
    override fun classify(error: Exception, occurredAt: Instant): TaskError {
        val classification = when (error) {
            is TaskExecutionException -> Classification(
                code = error.code,
                category = error.category,
                retryable = error.retryable
            )
            is IllegalArgumentException -> Classification(
                code = "INVALID_ARGUMENT",
                category = TaskErrorCategory.VALIDATION,
                retryable = false
            )
            is SecurityException -> Classification(
                code = "PERMISSION_DENIED",
                category = TaskErrorCategory.AUTHORIZATION,
                retryable = false
            )
            is IOException -> Classification(
                code = "IO_FAILURE",
                category = TaskErrorCategory.TRANSIENT,
                retryable = true
            )
            else -> Classification(
                code = "TASK_EXECUTION_FAILED",
                category = TaskErrorCategory.EXECUTION,
                retryable = true
            )
        }

        val type = error::class.simpleName ?: "Exception"
        val message = error.message?.takeIf(String::isNotBlank) ?: type
        val structuredDetails = (error as? TaskExecutionException)?.details.orEmpty()
        return TaskError(
            code = classification.code,
            category = classification.category,
            message = message,
            retryable = classification.retryable,
            details = mapOf("exceptionType" to type) + structuredDetails,
            occurredAt = occurredAt
        )
    }

    private data class Classification(
        val code: String,
        val category: TaskErrorCategory,
        val retryable: Boolean
    )
}
