package me.moiclathy.edb.agent.model

import java.time.Instant

data class AgentProposalDecision(
    val proposalId: String,
    val status: AgentProposalDecisionStatus,
    val decidedBy: Int,
    val workflowSha256: String,
    val reason: String? = null,
    val decidedAt: Instant
) {
    init {
        requireIdentifier(proposalId, "Agent proposal id")
        require(decidedBy > 0) { "Proposal decision user id must be greater than zero." }
        require(workflowSha256.matches(Regex("[0-9a-f]{64}"))) { "Workflow SHA-256 must be lowercase hexadecimal." }
        require(reason == null || reason.isNotBlank()) { "Proposal decision reason must not be blank." }
        require(status != AgentProposalDecisionStatus.REJECTED || reason != null) {
            "A rejected proposal requires a reason."
        }
    }
}

enum class AgentProposalDecisionStatus { APPROVED, REJECTED }
