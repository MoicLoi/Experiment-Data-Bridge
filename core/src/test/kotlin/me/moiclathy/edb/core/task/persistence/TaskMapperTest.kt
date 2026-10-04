package me.moiclathy.edb.core.task.persistence

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskPriority
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.retry.RetryPolicy
import me.moiclathy.edb.core.task.cancellation.TaskCancellationRequest
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.result.TaskResult
import me.moiclathy.edb.core.task.error.TaskError
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class TaskMapperTest {

    @Test
    fun `task survives persistence mapping round trip`() {
        val task = Task(
            id = 17,
            projectId = 5,
            taskType = "configuration-export",
            status = TaskStatus.RUNNING,
            priority = TaskPriority.HIGH,
            retryPolicy = RetryPolicy(
                maxAttempts = 3,
                initialDelay = Duration.ofSeconds(2),
                backoffMultiplier = 1.5,
                maxDelay = Duration.ofSeconds(20)
            ),
            attemptCount = 1,
            timeout = Duration.ofMinutes(5),
            cancellationRequest = TaskCancellationRequest(
                requestedAt = Instant.parse("2026-08-09T10:01:30Z"),
                requestedBy = 8
            ),
            progress = TaskProgress(
                percent = 40,
                message = "Processing",
                updatedAt = Instant.parse("2026-08-09T10:01:20Z")
            ),
            configSnapshot = "{\"version\":2}",
            triggerUserId = 8,
            startedAt = Instant.parse("2026-08-09T10:01:00Z"),
            completedAt = null,
            result = TaskResult(
                outputPath = "results/data.csv",
                contentType = "text/csv",
                metadata = mapOf("rows" to "42")
            ),
            error = null,
            createdAt = Instant.parse("2026-08-09T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-09T10:01:30Z")
        )

        assertEquals(task, TaskMapper.toModel(TaskMapper.toEntity(task)))

        val failed = task.copy(
            status = TaskStatus.FAILED,
            result = null,
            error = TaskError(
                code = "IO_FAILURE",
                category = TaskErrorCategory.TRANSIENT,
                message = "Temporary storage error",
                retryable = true,
                details = mapOf("host" to "storage-1"),
                occurredAt = Instant.parse("2026-08-09T10:01:25Z")
            )
        )
        assertEquals(failed, TaskMapper.toModel(TaskMapper.toEntity(failed)))
    }
}
