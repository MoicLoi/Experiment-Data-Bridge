package me.moiclathy.edb.core.task.workflow.ir

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineExecutionStatus
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.SequentialPipelineExecutor
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.WorkflowValidationException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class WorkflowIrCompilerTest {
    @Test
    fun `orders dependencies and applies input parameter and output bindings`() = runBlocking {
        val handlers = WorkflowStepHandlerRegistry(listOf(PutHandler(), NestedHandler()))
        val workflow = WorkflowIr(
            id = "ir-data-flow",
            version = "1.0.0",
            description = "Canonical data flow",
            inputs = listOf(WorkflowIrInput("seed", "input.seed", defaultValue = JsonPrimitive("raw.csv"))),
            outputs = listOf(WorkflowIrOutput("result", "result.file")),
            steps = listOf(
                WorkflowIrStep(
                    id = "consume",
                    type = "test.nested",
                    version = "1",
                    order = 20,
                    dependsOn = setOf("produce"),
                    contextBindings = listOf(WorkflowIrContextBinding("/payload/value", "intermediate")),
                    outputBindings = listOf(WorkflowIrOutputBinding("handler.output", "result.file"))
                ),
                WorkflowIrStep(
                    id = "produce",
                    type = "test.put",
                    version = "1",
                    order = 10,
                    contextBindings = listOf(WorkflowIrContextBinding("/value", "input.seed"))
                )
            )
        )

        val pipeline = WorkflowIrCompiler(handlers).compile(workflow)
        val result = SequentialPipelineExecutor().execute(pipeline)

        assertEquals(listOf("produce", "consume"), pipeline.steps.map { it.id })
        assertEquals(PipelineExecutionStatus.COMPLETED, result.status)
        assertEquals("raw.csv", result.context.getAs<String>("result.file"))
    }

    @Test
    fun `executes retry timeout and continue failure policies`() = runBlocking {
        var retryAttempts = 0
        val handlers = WorkflowStepHandlerRegistry(
            listOf(
                handler("test.retry") { _, context ->
                    retryAttempts++
                    if (retryAttempts == 1) PipelineStepResult.Failure("TRANSIENT", "try again", retryable = true)
                    else PipelineStepResult.Success(context.withValue("retried", true))
                },
                handler("test.slow") { _, context -> delay(100); PipelineStepResult.Success(context) },
                handler("test.marker") { _, context -> PipelineStepResult.Success(context.withValue("finished", true)) }
            )
        )
        val workflow = WorkflowIr(
            id = "policy-flow",
            version = "1",
            steps = listOf(
                WorkflowIrStep(
                    "retry", "test.retry", "1", 0,
                    policy = WorkflowIrStepPolicy(retry = WorkflowIrRetryPolicy(maxAttempts = 2, initialDelayMs = 0))
                ),
                WorkflowIrStep(
                    "slow", "test.slow", "1", 1, dependsOn = setOf("retry"),
                    policy = WorkflowIrStepPolicy(timeoutMs = 10, failureStrategy = WorkflowIrFailureStrategy.CONTINUE)
                ),
                WorkflowIrStep("finish", "test.marker", "1", 2, dependsOn = setOf("slow"))
            )
        )

        val result = SequentialPipelineExecutor().execute(WorkflowIrCompiler(handlers).compile(workflow))

        assertEquals(PipelineExecutionStatus.COMPLETED, result.status)
        assertEquals(2, retryAttempts)
        assertEquals(true, result.context.getAs<Boolean>("retried"))
        assertEquals(true, result.context.getAs<Boolean>("finished"))
        assertNotNull(result.context["${WorkflowIrCompiler.FAILURE_CONTEXT_PREFIX}slow"])
    }

    @Test
    fun `rejects dependency cycles before handlers are resolved`() {
        val workflow = WorkflowIr(
            id = "cycle",
            version = "1",
            steps = listOf(
                WorkflowIrStep("a", "test.a", "1", 0, dependsOn = setOf("b")),
                WorkflowIrStep("b", "test.b", "1", 1, dependsOn = setOf("a"))
            )
        )

        assertFailsWith<WorkflowValidationException> {
            WorkflowIrCompiler(WorkflowStepHandlerRegistry()).compile(workflow)
        }
    }

    private class PutHandler : WorkflowStepHandler {
        override val type = "test.put"
        override suspend fun execute(step: WorkflowStepDefinition, context: PipelineContext) =
            PipelineStepResult.Success(context.withValue("intermediate", step.parameters.getValue("value").jsonPrimitive.content))
    }

    private class NestedHandler : WorkflowStepHandler {
        override val type = "test.nested"
        override suspend fun execute(step: WorkflowStepDefinition, context: PipelineContext) = PipelineStepResult.Success(
            context.withValue(
                "handler.output",
                step.parameters.getValue("payload").jsonObject.getValue("value").jsonPrimitive.content
            )
        )
    }

    private fun handler(
        type: String,
        execute: suspend (WorkflowStepDefinition, PipelineContext) -> PipelineStepResult
    ) = object : WorkflowStepHandler {
        override val type = type
        override suspend fun execute(step: WorkflowStepDefinition, context: PipelineContext) = execute(step, context)
    }
}
