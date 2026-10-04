package me.moiclathy.edb.core.task.pipeline

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.error.TaskExecutionException
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.execution.TaskHandlerRegistry
import me.moiclathy.edb.core.task.progress.TaskProgress
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class PipelineTaskHandlerTest {
    private val executionContext = object : TaskExecutionContext {
        override suspend fun ensureNotCancelled() = Unit

        override suspend fun reportProgress(percent: Int, message: String?): TaskProgress =
            TaskProgress(percent, message, Instant.EPOCH)
    }

    @Test
    fun `registered task handler executes pipeline payload`() = runBlocking {
        val pipelines = PipelineRegistry(
            listOf(
                Pipeline(
                    id = "import-data",
                    steps = listOf(
                        PipelineStep("normalize") { context ->
                            PipelineStepResult.Success(
                                context.withValue("normalized", context["source"].toString().uppercase())
                            )
                        }
                    )
                )
            )
        )
        val handler = PipelineTaskHandler(pipelines, SequentialPipelineExecutor())
        val taskHandlers = TaskHandlerRegistry(listOf(handler))
        val task = pipelineTask("import-data", mapOf("source" to "lab"))

        val result = assertNotNull(taskHandlers.find(PipelineTaskHandler.TASK_TYPE))
            .execute(task, executionContext)

        assertEquals("import-data", result?.metadata?.get("pipelineId"))
        assertEquals("normalize", result?.metadata?.get("completedSteps"))
        assertEquals("LAB", result?.metadata?.get("context.normalized"))
    }

    @Test
    fun `pipeline failure stops later steps and keeps structured error`() = runBlocking {
        var laterStepExecuted = false
        val pipeline = Pipeline(
            id = "validate-data",
            steps = listOf(
                PipelineStep("validate") {
                    PipelineStepResult.Failure("INVALID_DATA", "Data is invalid.")
                },
                PipelineStep("persist") { context ->
                    laterStepExecuted = true
                    PipelineStepResult.Success(context)
                }
            )
        )
        val handler = PipelineTaskHandler(
            PipelineRegistry(listOf(pipeline)),
            SequentialPipelineExecutor()
        )

        val failure = assertFailsWith<TaskExecutionException> {
            handler.execute(pipelineTask(pipeline.id), executionContext)
        }

        assertEquals("INVALID_DATA", failure.code)
        assertEquals("validate", failure.details["stepId"])
        assertFalse(failure.retryable)
        assertFalse(laterStepExecuted)
    }

    private fun pipelineTask(
        pipelineId: String,
        context: Map<String, String> = emptyMap()
    ) = Task.pending(
        projectId = 1,
        taskType = PipelineTaskHandler.TASK_TYPE,
        configSnapshot = PipelineTaskPayloadCodec.encode(
            PipelineTaskPayload(pipelineId, context)
        ),
        triggerUserId = 2,
        createdAt = Instant.parse("2026-08-10T10:00:00Z")
    )
}
