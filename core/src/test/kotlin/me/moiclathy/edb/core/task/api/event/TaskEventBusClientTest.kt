package me.moiclathy.edb.core.task.api.event

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.event.CoroutineEventBus
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.application.SubmitPipelineTaskCommand
import me.moiclathy.edb.core.task.application.TaskApplicationService
import me.moiclathy.edb.core.task.pipeline.PipelineTaskHandler
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskEventBusClientTest {
    @Test
    fun `uses suspend functions for concurrent correlated task requests`() = runBlocking {
        val bus = CoroutineEventBus()
        val service = mockk<TaskApplicationService>()
        val task = task()
        val command = SubmitPipelineTaskCommand(7, "import-data", requestedBy = 11)
        coEvery { service.submitPipelineTask(command) } returns task
        every { service.getTask(71, 11) } returns task
        coEvery { service.requestCancellation(71, 11) } returns task
        val api = TaskEventBusApi(bus, service).also { it.start() }
        val client = TaskEventBusClient(bus)

        try {
            val results = listOf(
                async { client.submitPipelineTask(command) },
                async { client.getTask(71, 11) },
                async { client.requestCancellation(71, 11) }
            ).awaitAll()

            assertEquals(listOf(71, 71, 71), results.map { it.id })
        } finally {
            client.close()
            api.close()
            bus.close()
        }
    }

    @Test
    fun `throws typed timeout when no API subscriber responds`() = runBlocking {
        val bus = CoroutineEventBus()
        val client = TaskEventBusClient(
            eventBus = bus,
            responseTimeout = Duration.ofMillis(50)
        )

        try {
            val failure = assertFailsWith<TaskEventBusTimeoutException> {
                client.getTask(71, 11)
            }
            assertEquals(TaskApiOperation.GET_TASK, failure.operation)
            assertEquals("TASK_API_TIMEOUT", failure.code)
        } finally {
            client.close()
            bus.close()
        }
    }

    private fun task() = Task.pending(
        projectId = 7,
        taskType = PipelineTaskHandler.TASK_TYPE,
        configSnapshot = null,
        triggerUserId = 11,
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    ).copy(id = 71)
}
