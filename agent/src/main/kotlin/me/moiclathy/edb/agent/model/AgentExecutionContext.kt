package me.moiclathy.edb.agent.model

data class AgentExecutionContext(
    val sessionId: String,
    val runId: String,
    val userId: Int,
    val projectId: Int,
    val correlationId: String
) {
    init {
        requireIdentifier(sessionId, "Agent session id")
        requireIdentifier(runId, "Agent run id")
        require(userId > 0) { "Agent execution user id must be greater than zero." }
        require(projectId > 0) { "Agent execution project id must be greater than zero." }
        requireIdentifier(correlationId, "Agent correlation id")
    }
}
