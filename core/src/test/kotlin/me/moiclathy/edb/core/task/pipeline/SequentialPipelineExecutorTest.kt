package me.moiclathy.edb.core.task.pipeline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SequentialPipelineExecutorTest {
    private val executor = SequentialPipelineExecutor()

    @Test
    fun `executes steps in order and passes context forward`() = runBlocking {
        val executionOrder = mutableListOf<String>()
        val pipeline = Pipeline(
            id = "research-import",
            steps = listOf(
                PipelineStep("load") { context ->
                    executionOrder += "load"
                    PipelineStepResult.Success(context.withValue("rows", 12))
                },
                PipelineStep("summarize") { context ->
                    executionOrder += "summarize"
                    val rows = requireNotNull(context.getAs<Int>("rows"))
                    PipelineStepResult.Success(context.withValue("summary", "$rows rows"))
                }
            )
        )

        val result = executor.execute(pipeline)

        assertEquals(PipelineExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf("load", "summarize"), executionOrder)
        assertEquals(listOf("load", "summarize"), result.completedStepIds)
        assertEquals("12 rows", result.context.getAs<String>("summary"))
        assertEquals(null, result.failure)
    }

    @Test
    fun `stops before later steps after failure`() = runBlocking {
        var laterStepExecuted = false
        val pipeline = Pipeline(
            id = "failing-import",
            steps = listOf(
                PipelineStep("load") { context ->
                    PipelineStepResult.Success(context.withValue("loaded", true))
                },
                PipelineStep("validate") {
                    PipelineStepResult.Failure("INVALID_DATA", "Input data is invalid.")
                },
                PipelineStep("persist") { context ->
                    laterStepExecuted = true
                    PipelineStepResult.Success(context)
                }
            )
        )

        val result = executor.execute(pipeline)

        assertEquals(PipelineExecutionStatus.FAILED, result.status)
        assertEquals(listOf("load"), result.completedStepIds)
        assertEquals("validate", result.failure?.stepId)
        assertEquals("INVALID_DATA", result.failure?.code)
        assertEquals(true, result.context.getAs<Boolean>("loaded"))
        assertFalse(laterStepExecuted)
    }
}
