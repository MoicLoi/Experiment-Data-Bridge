package me.moiclathy.edb.core.task.execution

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.retry.RetryPolicy
import me.moiclathy.edb.core.task.cancellation.TaskCancellationRequest
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import me.moiclathy.edb.core.task.result.TaskResult
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DefaultTaskExecutorTest {

    @Test
    fun `handler exception persists task as failed`() = runBlocking {
        val repository = mockk<TaskRepository>()
        val captured = slot<Task>()
        every { repository.update(capture(captured)) } answers { captured.captured }
        val handler = object : TaskHandler {
            override val taskType = "failing-task"

            override suspend fun execute(
                task: Task,
                context: TaskExecutionContext
            ): TaskResult? {
                error("processing failed")
            }
        }
        val executor = DefaultTaskExecutor(
            registry = TaskHandlerRegistry(listOf(handler)),
            repository = repository,
            clock = Clock.fixed(
                Instant.parse("2026-08-09T10:02:00Z"),
                ZoneOffset.UTC
            )
        )
        val running = Task(
            id = 21,
            projectId = 4,
            taskType = handler.taskType,
            status = TaskStatus.RUNNING,
            attemptCount = 1,
            triggerUserId = 9,
            startedAt = Instant.parse("2026-08-09T10:01:00Z"),
            createdAt = Instant.parse("2026-08-09T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-09T10:01:00Z")
        )
        every { repository.findById(21) } returns running

        val failed = executor.execute(running)

        assertEquals(TaskStatus.FAILED, failed.status)
        assertEquals(Instant.parse("2026-08-09T10:02:00Z"), failed.completedAt)
        assertTrue(failed.error?.message.orEmpty().contains("processing failed"))
        assertEquals(TaskErrorCategory.EXECUTION, failed.error?.category)
        assertEquals("TASK_EXECUTION_FAILED", failed.error?.code)
    }

    @Test
    fun `timeout schedules retry with backoff when attempts remain`() = runBlocking {
        val repository = mockk<TaskRepository>()
        val captured = slot<Task>()
        every { repository.update(capture(captured)) } answers { captured.captured }
        val handler = object : TaskHandler {
            override val taskType = "slow-task"

            override suspend fun execute(
                task: Task,
                context: TaskExecutionContext
            ): TaskResult? {
                delay(1_000)
                return null
            }
        }
        val failureTime = Instant.parse("2026-08-09T10:02:00Z")
        val executor = DefaultTaskExecutor(
            registry = TaskHandlerRegistry(listOf(handler)),
            repository = repository,
            clock = Clock.fixed(failureTime, ZoneOffset.UTC)
        )
        val running = Task(
            id = 22,
            projectId = 4,
            taskType = handler.taskType,
            status = TaskStatus.RUNNING,
            retryPolicy = RetryPolicy(
                maxAttempts = 2,
                initialDelay = Duration.ofSeconds(5),
                maxDelay = Duration.ofSeconds(30)
            ),
            attemptCount = 1,
            timeout = Duration.ofMillis(10),
            triggerUserId = 9,
            startedAt = Instant.parse("2026-08-09T10:01:00Z"),
            createdAt = Instant.parse("2026-08-09T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-09T10:01:00Z")
        )
        every { repository.findById(22) } returns running

        val retrying = executor.execute(running)

        assertEquals(TaskStatus.RETRYING, retrying.status)
        assertEquals(failureTime.plusSeconds(5), retrying.nextAttemptAt)
        assertEquals(null, retrying.completedAt)
        assertTrue(retrying.error?.message.orEmpty().contains("timed out"))
        assertEquals(TaskErrorCategory.TIMEOUT, retrying.error?.category)
    }

    @Test
    fun `persisted cancellation request stops task before handler execution`() = runBlocking {
        val repository = mockk<TaskRepository>()
        val captured = slot<Task>()
        every { repository.update(capture(captured)) } answers { captured.captured }
        var handlerInvoked = false
        val handler = object : TaskHandler {
            override val taskType = "cancelled-task"

            override suspend fun execute(
                task: Task,
                context: TaskExecutionContext
            ): TaskResult? {
                handlerInvoked = true
                return null
            }
        }
        val executor = DefaultTaskExecutor(
            registry = TaskHandlerRegistry(listOf(handler)),
            repository = repository,
            clock = Clock.fixed(
                Instant.parse("2026-08-09T10:02:00Z"),
                ZoneOffset.UTC
            )
        )
        val running = Task(
            id = 23,
            projectId = 4,
            taskType = handler.taskType,
            status = TaskStatus.RUNNING,
            attemptCount = 1,
            cancellationRequest = TaskCancellationRequest(
                requestedAt = Instant.parse("2026-08-09T10:01:30Z"),
                requestedBy = 7
            ),
            triggerUserId = 9,
            startedAt = Instant.parse("2026-08-09T10:01:00Z"),
            createdAt = Instant.parse("2026-08-09T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-09T10:01:30Z")
        )
        every { repository.findById(23) } returns running

        val cancelled = executor.execute(running)

        assertEquals(TaskStatus.CANCELLED, cancelled.status)
        assertEquals(false, handlerInvoked)
    }

    @Test
    fun `handler result is persisted on completion`() = runBlocking {
        val repository = mockk<TaskRepository>()
        val captured = slot<Task>()
        every { repository.update(capture(captured)) } answers { captured.captured }
        val expectedResult = TaskResult(
            outputPath = "results/output.csv",
            contentType = "text/csv",
            metadata = mapOf("rows" to "42")
        )
        val handler = object : TaskHandler {
            override val taskType = "result-task"

            override suspend fun execute(
                task: Task,
                context: TaskExecutionContext
            ): TaskResult = expectedResult
        }
        val executor = DefaultTaskExecutor(
            registry = TaskHandlerRegistry(listOf(handler)),
            repository = repository,
            clock = Clock.fixed(
                Instant.parse("2026-08-09T10:02:00Z"),
                ZoneOffset.UTC
            )
        )
        val running = Task(
            id = 25,
            projectId = 4,
            taskType = handler.taskType,
            status = TaskStatus.RUNNING,
            attemptCount = 1,
            triggerUserId = 9,
            startedAt = Instant.parse("2026-08-09T10:01:00Z"),
            createdAt = Instant.parse("2026-08-09T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-09T10:01:00Z")
        )
        every { repository.findById(25) } returns running

        val completed = executor.execute(running)

        assertEquals(TaskStatus.COMPLETED, completed.status)
        assertEquals(expectedResult, completed.result)
        assertEquals(null, completed.error)
    }
}
