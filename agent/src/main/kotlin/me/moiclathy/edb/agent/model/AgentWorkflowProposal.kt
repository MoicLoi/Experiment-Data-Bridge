package me.moiclathy.edb.agent.model

import java.time.Instant

data class AgentWorkflowProposal(
    val id: String,
    val runId: String,
    val workflowIrJson: String,
    val explanation: String,
    val assumptions: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val validation: AgentProposalValidation,
    val approvalRequired: Boolean = true,
    val createdAt: Instant
) {
    init {
        requireIdentifier(id, "Agent Workflow proposal id")
        requireIdentifier(runId, "Agent run id")
        require(workflowIrJson.isNotBlank()) { "Agent Workflow proposal IR must not be blank." }
        require(explanation.isNotBlank()) { "Agent Workflow proposal explanation must not be blank." }
        require(assumptions.none(String::isBlank)) { "Agent Workflow assumptions must not be blank." }
        require(warnings.none(String::isBlank)) { "Agent Workflow warnings must not be blank." }
    }
}

data class AgentProposalValidation(
    val valid: Boolean,
    val executionOrder: List<String> = emptyList(),
    val errors: List<AgentProposalValidationError> = emptyList()
) {
    init {
        require(valid != errors.isNotEmpty()) { "A valid proposal cannot contain errors, and an invalid proposal must contain errors." }
        require(executionOrder.none(String::isBlank)) { "Proposal execution-order step ids must not be blank." }
    }
}

data class AgentProposalValidationError(val path: String, val message: String) {
    init {
        require(path.isNotBlank()) { "Proposal validation error path must not be blank." }
        require(message.isNotBlank()) { "Proposal validation error message must not be blank." }
    }
}
