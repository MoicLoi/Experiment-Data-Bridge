package me.moiclathy.edb.core.task.execution

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.checkpoint.TaskCheckpoint
import me.moiclathy.edb.core.task.checkpoint.TaskCheckpointRepository
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.retry.RetryPolicy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class TaskCheckpointExecutionTest {
    private val now = Instant.parse("2026-08-15T12:00:00Z")

    @Test
    fun `matching handler version resumes checkpoint and completion removes it`() = runBlocking {
        val task = runningTask(41, "recoverable")
        val tasks = taskRepository(task)
        val checkpoints = RecordingCheckpointRepository(
            TaskCheckpoint(41, "python-2.1", "chunk-7", mapOf("partial" to "data/part-7"), updatedAt = now)
        )
        var loaded: TaskCheckpoint? = null
        val handler = object : CheckpointTaskHandler {
            override val taskType = "recoverable"
            override val handlerVersion = "python-2.1"
            override suspend fun execute(task: Task, context: TaskExecutionContext) = null.also {
                loaded = context.loadCheckpoint()
                context.saveCheckpoint("chunk-8", mapOf("partial" to "data/part-8"))
            }
        }

        val completed = executor(tasks, checkpoints, handler).execute(task)

        assertEquals("chunk-7", loaded?.cursor)
        assertEquals(TaskStatus.COMPLETED, completed.status)
        assertNull(checkpoints.current)
        assertEquals(listOf("chunk-8"), checkpoints.savedCursors)
    }

    @Test
    fun `ordinary handler discards stale checkpoint and starts from beginning`() = runBlocking {
        val task = runningTask(42, "ordinary")
        val tasks = taskRepository(task)
        val checkpoints = RecordingCheckpointRepository(
            TaskCheckpoint(42, "old", "step-4", updatedAt = now)
        )
        var loaded: TaskCheckpoint? = checkpoints.current
        val handler = object : TaskHandler {
            override val taskType = "ordinary"
            override suspend fun execute(task: Task, context: TaskExecutionContext) = null.also {
                loaded = context.loadCheckpoint()
            }
        }

        executor(tasks, checkpoints, handler).execute(task)

        assertNull(loaded)
        assertNull(checkpoints.current)
    }

    @Test
    fun `changed handler version invalidates old checkpoint`() = runBlocking {
        val task = runningTask(43, "recoverable")
        val tasks = taskRepository(task)
        val checkpoints = RecordingCheckpointRepository(
            TaskCheckpoint(43, "python-1", "row-100", updatedAt = now)
        )
        var loaded: TaskCheckpoint? = checkpoints.current
        val handler = object : CheckpointTaskHandler {
            override val taskType = "recoverable"
            override val handlerVersion = "python-2"
            override suspend fun execute(task: Task, context: TaskExecutionContext) = null.also {
                loaded = context.loadCheckpoint()
            }
        }

        executor(tasks, checkpoints, handler).execute(task)

        assertNull(loaded)
    }

    private fun executor(
        tasks: TaskRepository,
        checkpoints: TaskCheckpointRepository,
        handler: TaskHandler
    ) = DefaultTaskExecutor(
        TaskHandlerRegistry(listOf(handler)), tasks, Clock.fixed(now, ZoneOffset.UTC),
        checkpointRepository = checkpoints
    )

    private fun taskRepository(task: Task): TaskRepository = mockk<TaskRepository>().also { repository ->
        val updated = slot<Task>()
        every { repository.findById(requireNotNull(task.id)) } answers {
            if (updated.isCaptured) updated.captured else task
        }
        every { repository.update(capture(updated)) } answers { updated.captured }
    }

    private fun runningTask(id: Int, type: String) = Task(
        id = id,
        projectId = 1,
        taskType = type,
        status = TaskStatus.RUNNING,
        retryPolicy = RetryPolicy(maxAttempts = 3),
        attemptCount = 2,
        triggerUserId = 1,
        startedAt = now.minusSeconds(30),
        createdAt = now.minusSeconds(60),
        updatedAt = now.minusSeconds(30)
    )

    private class RecordingCheckpointRepository(initial: TaskCheckpoint?) : TaskCheckpointRepository {
        var current = initial
        val savedCursors = mutableListOf<String>()
        override fun findByTaskId(taskId: Int) = current
        override fun save(checkpoint: TaskCheckpoint) = checkpoint.also {
            current = it
            savedCursors += it.cursor
        }
        override fun deleteByTaskId(taskId: Int) { current = null }
    }
}
