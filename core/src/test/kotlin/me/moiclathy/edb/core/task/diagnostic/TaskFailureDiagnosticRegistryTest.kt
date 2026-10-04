package me.moiclathy.edb.core.task.diagnostic

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.error.TaskError
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class TaskFailureDiagnosticRegistryTest {
    private val registry = TaskFailureDiagnosticRegistry.default()

    @Test
    fun `maps worker transport failures to safe start action`() {
        val diagnostic = registry.diagnose(taskWithError("ANALYSIS_IO_FAILURE", TaskErrorCategory.TRANSIENT, "External analysis worker request failed"))

        assertEquals("WORKER_UNAVAILABLE", diagnostic?.code)
        assertEquals(TaskRemediationAction.START_WORKER, diagnostic?.action)
        assertEquals(true, diagnostic?.automaticActionSafe)
    }

    @Test
    fun `does not invent remediation for unknown execution failures`() {
        assertNull(registry.diagnose(taskWithError("ALGORITHM_FAILED", TaskErrorCategory.EXECUTION, "Model rejected the input")))
    }

    private fun taskWithError(code: String, category: TaskErrorCategory, message: String): Task {
        val now = Instant.parse("2026-08-15T12:00:00Z")
        return Task.pending(1, "DATA_FILE_ANALYSIS", null, 1, now).copy(
            error = TaskError(code, category, message, retryable = false, occurredAt = now)
        )
    }
}
