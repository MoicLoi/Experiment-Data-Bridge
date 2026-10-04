package me.moiclathy.edb.agent.policy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AgentPolicyEvaluatorTest {
    private val evaluator = AgentPolicyEvaluator()
    private val policy = AgentPolicy(
        id = "workflow-planner",
        allowedCapabilities = setOf(
            AgentCapability.DISCOVER_STEPS,
            AgentCapability.VALIDATE_WORKFLOW,
            AgentCapability.SUBMIT_TASK
        ),
        allowedToolNames = setOf("searchWorkflowSteps", "validateWorkflowIr", "submitWorkflowTask"),
        maxToolCallsPerRun = 3,
        maxWorkflowSteps = 2,
        maxPromptCharacters = 100,
        maxTokensPerRun = 50
    )

    @Test
    fun `allows read operation and requires approval for mutation`() {
        assertEquals(
            AgentPolicyDecision.Allowed,
            evaluator.evaluate(policy, AgentActionRequest(AgentCapability.VALIDATE_WORKFLOW, "validateWorkflowIr"))
        )
        assertIs<AgentPolicyDecision.ApprovalRequired>(
            evaluator.evaluate(policy, AgentActionRequest(AgentCapability.SUBMIT_TASK, "submitWorkflowTask"))
        )
    }

    @Test
    fun `denies missing capability tool and exhausted limits`() {
        assertEquals("AGENT_CAPABILITY_DENIED", denied(AgentActionRequest(AgentCapability.CANCEL_TASK, "cancelTask")).code)
        assertEquals("AGENT_TOOL_DENIED", denied(AgentActionRequest(AgentCapability.VALIDATE_WORKFLOW, "unknown")).code)
        assertEquals("AGENT_TOOL_CALL_LIMIT", denied(AgentActionRequest(AgentCapability.VALIDATE_WORKFLOW, "validateWorkflowIr", toolCallsSoFar = 3)).code)
        assertEquals("AGENT_WORKFLOW_STEP_LIMIT", denied(AgentActionRequest(AgentCapability.VALIDATE_WORKFLOW, "validateWorkflowIr", proposedWorkflowSteps = 3)).code)
    }

    private fun denied(request: AgentActionRequest) =
        assertIs<AgentPolicyDecision.Denied>(evaluator.evaluate(policy, request))
}
