package me.moiclathy.edb.core.task.api.event

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.moiclathy.edb.core.event.CoroutineEventBus
import me.moiclathy.edb.core.event.subscribe
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.application.SubmitPipelineTaskCommand
import me.moiclathy.edb.core.task.application.TaskApplicationService
import me.moiclathy.edb.core.task.pipeline.PipelineTaskHandler
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TaskEventBusApiTest {
    @Test
    fun `handles submit query and cancellation with correlated responses`() = runBlocking {
        val bus = CoroutineEventBus()
        val service = mockk<TaskApplicationService>()
        val task = Task.pending(
            projectId = 7,
            taskType = PipelineTaskHandler.TASK_TYPE,
            configSnapshot = null,
            triggerUserId = 11,
            createdAt = Instant.parse("2026-08-10T12:00:00Z")
        ).copy(id = 61)
        val command = SubmitPipelineTaskCommand(
            projectId = 7,
            pipelineId = "import-data",
            requestedBy = 11
        )
        coEvery { service.submitPipelineTask(command) } returns task
        every { service.getTask(61, 11) } returns task
        coEvery { service.requestCancellation(61, 11) } returns task

        val responses = Channel<TaskApiResponseEvent>(Channel.UNLIMITED)
        val responseSubscription = bus.subscribe<TaskApiResponseEvent> { responses.send(it) }
        val api = TaskEventBusApi(
            eventBus = bus,
            service = service,
            clock = Clock.fixed(Instant.parse("2026-08-10T12:01:00Z"), ZoneOffset.UTC)
        )

        try {
            assertTrue(api.start())
            bus.publish(SubmitPipelineTaskRequestEvent(command, correlationId = "submit-1"))
            bus.publish(GetTaskRequestEvent(61, 11, correlationId = "get-1"))
            bus.publish(CancelTaskRequestEvent(61, 11, correlationId = "cancel-1"))

            val received = withTimeout(2_000) {
                List(3) { responses.receive() }.associateBy { it.correlationId }
            }

            assertIs<PipelineTaskSubmittedEvent>(received["submit-1"])
            assertIs<TaskFoundEvent>(received["get-1"])
            assertIs<TaskCancellationAcceptedEvent>(received["cancel-1"])
            assertEquals(61, (received["get-1"] as TaskFoundEvent).task.id)
        } finally {
            api.close()
            responseSubscription.cancel()
            responses.close()
            bus.close()
        }
    }
}
