package me.moiclathy.edb.agent.policy

import java.time.Duration

data class AgentPolicy(
    val id: String,
    val allowedCapabilities: Set<AgentCapability>,
    val approvalRequiredCapabilities: Set<AgentCapability> =
        allowedCapabilities.intersect(DEFAULT_APPROVAL_CAPABILITIES),
    val allowedToolNames: Set<String> = emptySet(),
    val maxToolCallsPerRun: Int = 20,
    val maxWorkflowSteps: Int = 25,
    val maxPromptCharacters: Int = 64_000,
    val maxTokensPerRun: Long = 100_000,
    val runTimeout: Duration = Duration.ofMinutes(5)
) {
    init {
        require(id.isNotBlank() && id == id.trim()) { "Agent policy id must not be blank or padded." }
        require(approvalRequiredCapabilities.all { it in allowedCapabilities }) {
            "Approval-required Agent capabilities must also be allowed."
        }
        require(allowedToolNames.none { it.isBlank() || it != it.trim() }) {
            "Allowed Agent tool names must not be blank or padded."
        }
        require(maxToolCallsPerRun > 0) { "Maximum Agent tool calls must be greater than zero." }
        require(maxWorkflowSteps > 0) { "Maximum Agent Workflow steps must be greater than zero." }
        require(maxPromptCharacters > 0) { "Maximum Agent prompt characters must be greater than zero." }
        require(maxTokensPerRun > 0) { "Maximum Agent tokens must be greater than zero." }
        require(!runTimeout.isZero && !runTimeout.isNegative) { "Agent run timeout must be greater than zero." }
    }

    companion object {
        val DEFAULT_APPROVAL_CAPABILITIES = setOf(
            AgentCapability.COMPILE_WORKFLOW,
            AgentCapability.SUBMIT_TASK,
            AgentCapability.CANCEL_TASK
        )
    }
}

enum class AgentCapability {
    DISCOVER_STEPS,
    VALIDATE_WORKFLOW,
    COMPILE_WORKFLOW,
    SUBMIT_TASK,
    QUERY_TASK,
    CANCEL_TASK,
    READ_DATA_FILES
}
