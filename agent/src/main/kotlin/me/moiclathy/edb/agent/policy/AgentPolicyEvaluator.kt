package me.moiclathy.edb.agent.policy

data class AgentActionRequest(
    val capability: AgentCapability,
    val toolName: String,
    val toolCallsSoFar: Int = 0,
    val proposedWorkflowSteps: Int = 0,
    val promptCharacters: Int = 0,
    val consumedTokens: Long = 0
) {
    init {
        require(toolName.isNotBlank() && toolName == toolName.trim()) { "Agent action tool name must not be blank or padded." }
        require(toolCallsSoFar >= 0) { "Agent tool call count must not be negative." }
        require(proposedWorkflowSteps >= 0) { "Proposed Workflow step count must not be negative." }
        require(promptCharacters >= 0) { "Agent prompt character count must not be negative." }
        require(consumedTokens >= 0) { "Consumed Agent tokens must not be negative." }
    }
}

class AgentPolicyEvaluator {
    fun evaluate(policy: AgentPolicy, request: AgentActionRequest): AgentPolicyDecision = when {
        request.capability !in policy.allowedCapabilities -> AgentPolicyDecision.Denied(
            "AGENT_CAPABILITY_DENIED",
            "Agent capability is not allowed by policy '${policy.id}': ${request.capability}"
        )
        policy.allowedToolNames.isNotEmpty() && request.toolName !in policy.allowedToolNames -> AgentPolicyDecision.Denied(
            "AGENT_TOOL_DENIED",
            "Agent tool is not allowed by policy '${policy.id}': ${request.toolName}"
        )
        request.toolCallsSoFar >= policy.maxToolCallsPerRun -> limit("AGENT_TOOL_CALL_LIMIT", "Agent tool-call limit reached.")
        request.proposedWorkflowSteps > policy.maxWorkflowSteps -> limit("AGENT_WORKFLOW_STEP_LIMIT", "Proposed Workflow exceeds the Agent step limit.")
        request.promptCharacters > policy.maxPromptCharacters -> limit("AGENT_PROMPT_LIMIT", "Agent prompt exceeds the character limit.")
        request.consumedTokens > policy.maxTokensPerRun -> limit("AGENT_TOKEN_LIMIT", "Agent token limit exceeded.")
        request.capability in policy.approvalRequiredCapabilities -> AgentPolicyDecision.ApprovalRequired(
            request.capability,
            "Agent capability requires explicit approval: ${request.capability}"
        )
        else -> AgentPolicyDecision.Allowed
    }

    private fun limit(code: String, message: String) = AgentPolicyDecision.Denied(code, message)
}

sealed interface AgentPolicyDecision {
    data object Allowed : AgentPolicyDecision
    data class ApprovalRequired(val capability: AgentCapability, val reason: String) : AgentPolicyDecision
    data class Denied(val code: String, val reason: String) : AgentPolicyDecision
}
