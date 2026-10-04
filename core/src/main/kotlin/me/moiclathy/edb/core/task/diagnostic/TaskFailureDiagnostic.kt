package me.moiclathy.edb.core.task.diagnostic

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.error.TaskErrorCategory

enum class TaskRemediationAction { START_WORKER, REVIEW_PARAMETERS, CHECK_CONFIGURATION, CONVERT_INPUT, RETRY }

data class TaskFailureDiagnostic(
    val code: String,
    val title: String,
    val message: String,
    val action: TaskRemediationAction? = null,
    val actionLabel: String? = null,
    val automaticActionSafe: Boolean = false
)

fun interface TaskFailureDiagnosticRule { fun diagnose(task: Task): TaskFailureDiagnostic? }

/** Deterministic diagnostics that describe remediation without executing it. */
class TaskFailureDiagnosticRegistry(private val rules: List<TaskFailureDiagnosticRule>) {
    fun diagnose(task: Task): TaskFailureDiagnostic? =
        if (task.error == null) null else rules.firstNotNullOfOrNull { it.diagnose(task) }

    companion object {
        fun default() = TaskFailureDiagnosticRegistry(listOf(
            exactCode("ANALYSIS_HANDLER_NOT_FOUND", "HANDLER_NOT_CONFIGURED", "Analysis handler is unavailable", "Check the configured analysis handler and try again.", TaskRemediationAction.CHECK_CONFIGURATION, "Review configuration"),
            exactCode("INVALID_ANALYSIS_PAYLOAD", "INVALID_PARAMETERS", "Analysis parameters are invalid", "Review the required fields and parameter values.", TaskRemediationAction.REVIEW_PARAMETERS, "Review parameters"),
            exactCode("DATA_FILE_FORMAT_UNSUPPORTED", "UNSUPPORTED_INPUT_FORMAT", "Input format is unsupported", "Convert the source file to a supported format.", TaskRemediationAction.CONVERT_INPUT, "Convert input"),
            category(TaskErrorCategory.TIMEOUT, "TASK_TIMED_OUT", "Task timed out", "Retry the task or ask an administrator to review its timeout.", TaskRemediationAction.RETRY, "Retry"),
            TaskFailureDiagnosticRule { task ->
                val error = requireNotNull(task.error)
                val text = "${error.code} ${error.message}".lowercase()
                if (error.code == "ANALYSIS_IO_FAILURE" || listOf("connection refused", "worker request failed", "failed to connect", "connection reset").any(text::contains)) {
                    TaskFailureDiagnostic("WORKER_UNAVAILABLE", "Analysis Worker is unavailable", "Start the configured Worker, wait until it is healthy, and submit again.", TaskRemediationAction.START_WORKER, "Start Worker and retry", true)
                } else null
            }
        ))

        private fun exactCode(errorCode: String, code: String, title: String, message: String, action: TaskRemediationAction, actionLabel: String) =
            TaskFailureDiagnosticRule { task -> if (task.error?.code == errorCode) TaskFailureDiagnostic(code, title, message, action, actionLabel) else null }

        private fun category(category: TaskErrorCategory, code: String, title: String, message: String, action: TaskRemediationAction, actionLabel: String) =
            TaskFailureDiagnosticRule { task -> if (task.error?.category == category) TaskFailureDiagnostic(code, title, message, action, actionLabel) else null }
    }
}
