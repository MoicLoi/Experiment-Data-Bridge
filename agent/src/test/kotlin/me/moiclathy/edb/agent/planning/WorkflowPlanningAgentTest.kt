package me.moiclathy.edb.agent.planning

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.model.AgentDefinition
import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentTokenUsage
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.policy.AgentPolicy
import me.moiclathy.edb.agent.provider.AgentModelResponse
import me.moiclathy.edb.agent.provider.AgentRequestedToolCall
import me.moiclathy.edb.agent.support.ScriptedAgentChatModel
import me.moiclathy.edb.agent.tools.WorkflowPlanningTools
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.workflow.SetContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.application.WorkflowIrApplicationService
import me.moiclathy.edb.core.task.workflow.catalog.WorkflowStepCatalogService
import me.moiclathy.edb.core.task.workflow.ir.JsonWorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCompiler
import me.moiclathy.edb.core.task.workflow.schema.BuiltInWorkflowStepSchemas
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepSchemaRegistry
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowPlanningAgentTest {
    @Test
    fun `discovers schema repairs invalid IR and returns validated proposal without registration`() = runBlocking {
        val fixture = fixture(
            AgentModelResponse(
                toolCalls = listOf(AgentRequestedToolCall("call-1", WorkflowPlanningTools.LIST_STEPS, JsonObject(emptyMap()))),
                tokenUsage = AgentTokenUsage(10, 2)
            ),
            AgentModelResponse(
                toolCalls = listOf(AgentRequestedToolCall("call-2", WorkflowPlanningTools.GET_STEP_SCHEMA, buildJsonObject {
                    put("type", "context.set"); put("version", "1")
                })),
                tokenUsage = AgentTokenUsage(8, 2)
            ),
            AgentModelResponse(structuredOutput = proposal(INVALID_WORKFLOW, "Initial proposal")),
            AgentModelResponse(structuredOutput = proposal(VALID_WORKFLOW, "Corrected proposal"))
        )

        val result = fixture.agent.plan(command(maxRepairAttempts = 2))

        assertEquals("proposal-1", result.id)
        assertTrue(result.validation.valid)
        assertEquals(listOf("set-value"), result.validation.executionOrder)
        assertTrue(result.approvalRequired)
        assertTrue(result.workflowIrJson.contains("\"context.set\""))
        assertTrue(fixture.model.requests[1].messages.any { it.content.contains("context.set") })
        assertTrue(fixture.model.requests.last().messages.any { it.content.contains("validationErrors") })
        assertFalse(fixture.registry.registeredIds().contains("planned-flow"))
    }

    @Test
    fun `fails with bounded repair error when model never produces valid IR`() = runBlocking {
        val fixture = fixture(
            AgentModelResponse(structuredOutput = proposal(INVALID_WORKFLOW, "First invalid proposal")),
            AgentModelResponse(structuredOutput = proposal(INVALID_WORKFLOW, "Second invalid proposal"))
        )

        val failure = assertFailsWith<WorkflowPlanningException> {
            fixture.agent.plan(command(maxRepairAttempts = 1))
        }

        assertEquals("AGENT_WORKFLOW_REPAIR_EXHAUSTED", failure.code)
        assertEquals(2, fixture.model.requests.size)
        assertTrue(fixture.registry.registeredIds().isEmpty())
    }

    private fun fixture(vararg responses: AgentModelResponse): Fixture {
        val handlers = WorkflowStepHandlerRegistry(listOf(SetContextWorkflowStepHandler()))
        val schemas = WorkflowStepSchemaRegistry(BuiltInWorkflowStepSchemas.all())
        val registry = PipelineRegistry()
        val codec = JsonWorkflowIrCodec()
        val tools = WorkflowPlanningTools(
            WorkflowStepCatalogService(handlers, schemas),
            WorkflowIrApplicationService(codec, WorkflowIrCompiler(handlers, schemas = schemas), registry),
            codec
        )
        val model = ScriptedAgentChatModel(responses.asList())
        return Fixture(
            WorkflowPlanningAgent(
                model,
                tools,
                clock = Clock.fixed(Instant.parse("2026-08-11T13:00:00Z"), ZoneOffset.UTC),
                proposalIdSupplier = { "proposal-1" }
            ),
            model,
            registry
        )
    }

    private fun command(maxRepairAttempts: Int) = WorkflowPlanningCommand(
        context = AgentExecutionContext("session-1", "run-1", 7, 11, "correlation-1"),
        definition = AgentDefinition(
            id = "workflow-planner",
            version = "1",
            description = "Plans validated Workflow IR",
            systemPrompt = "You are the EDB Workflow planning agent.",
            modelProfileId = "fake-model",
            enabledTools = setOf(
                WorkflowPlanningTools.LIST_STEPS,
                WorkflowPlanningTools.GET_STEP_SCHEMA,
                WorkflowPlanningTools.VALIDATE_WORKFLOW
            ),
            policyId = "planning-read-only"
        ),
        policy = AgentPolicy(
            id = "planning-read-only",
            allowedCapabilities = setOf(AgentCapability.DISCOVER_STEPS, AgentCapability.VALIDATE_WORKFLOW),
            allowedToolNames = setOf(
                WorkflowPlanningTools.LIST_STEPS,
                WorkflowPlanningTools.GET_STEP_SCHEMA,
                WorkflowPlanningTools.VALIDATE_WORKFLOW
            ),
            maxToolCallsPerRun = 10
        ),
        userIntent = "Set the Pipeline context value result to ready.",
        maxRepairAttempts = maxRepairAttempts
    )

    private fun proposal(workflow: String, explanation: String) = buildJsonObject {
        put("workflow", Json.parseToJsonElement(workflow))
        put("explanation", explanation)
        put("assumptions", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("No external data is required")) })
        put("warnings", kotlinx.serialization.json.buildJsonArray { })
    }

    private data class Fixture(
        val agent: WorkflowPlanningAgent,
        val model: ScriptedAgentChatModel,
        val registry: PipelineRegistry
    )

    companion object {
        private val INVALID_WORKFLOW = """
            {"schemaVersion":1,"id":"planned-flow","version":"1","steps":[
              {"id":"set-value","type":"context.set","version":"1","order":0,"parameters":{"key":"result"}}
            ]}
        """.trimIndent()
        private val VALID_WORKFLOW = """
            {"schemaVersion":1,"id":"planned-flow","version":"1","steps":[
              {"id":"set-value","type":"context.set","version":"1","order":0,"parameters":{"key":"result","value":"ready"}}
            ]}
        """.trimIndent()
    }
}
