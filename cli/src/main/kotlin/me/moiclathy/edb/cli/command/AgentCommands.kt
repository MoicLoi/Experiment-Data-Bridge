package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.moiclathy.edb.cli.service.AgentApiClient
import me.moiclathy.edb.cli.service.AgentEventStreamClient
import me.moiclathy.edb.cli.service.AgentWatchStopReason
import me.moiclathy.edb.cli.service.TaskServerConfig

class AgentCommand : DescribedCommand("agent", "Plan and approve Workflows through the controlled Agent") { override fun run() = Unit }
class AgentProfileCommand : DescribedCommand("profile", "Discover configured Agent model profiles") { override fun run() = Unit }
class AgentSessionCommand : DescribedCommand("session", "Manage Agent planning sessions") { override fun run() = Unit }
class AgentRunCommand : DescribedCommand("run", "Inspect and control Agent runs") { override fun run() = Unit }
class AgentProposalCommand : DescribedCommand("proposal", "Review and submit Workflow proposals") { override fun run() = Unit }
class AgentAdminCommand : DescribedCommand("admin", "Administer Agent providers, metrics and retention") { override fun run() = Unit }
class AgentAdminRetentionCommand : DescribedCommand("retention", "Manage persisted Agent run retention") { override fun run() = Unit }

private typealias AgentFactory = (TaskServerConfig) -> AgentApiClient

class AgentProfileListCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("list", "List model profiles without exposing secrets") {
    override fun run() = command(root) { it.agentDocument("Model profiles", factory(root.taskServerConfig()).profiles()) }
}

class AgentQuotaCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("quota", "Show current user and project Agent Token quota") {
    private val projectId by argument("project-id").int()
    override fun run() = command(root) { it.agentDocument("Token quota", factory(root.taskServerConfig()).quota(projectId)) }
}

class AgentSessionCreateCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("create", "Create an Agent session for a project") {
    private val projectId by argument("project-id").int()
    private val agentId by option("--agent").default("workflow-planner")
    private val version by option("--version").default("1")
    override fun run() = command(root) { it.agentDocument("Session created", factory(root.taskServerConfig()).createSession(projectId, agentId, version)) }
}

class AgentSessionInfoCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("info", "Show one Agent session") {
    private val id by argument("session-id")
    override fun run() = command(root) { it.agentDocument("Session", factory(root.taskServerConfig()).session(id)) }
}

class AgentSessionCloseCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("close", "Close an Agent session") {
    private val id by argument("session-id")
    override fun run() = command(root) { it.agentDocument("Session closed", factory(root.taskServerConfig()).closeSession(id)) }
}

class AgentSessionMessagesCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("messages", "Show persisted Agent chat memory") {
    private val id by argument("session-id")
    override fun run() = command(root) { it.agentDocument("Session messages", factory(root.taskServerConfig()).messages(id)) }
}

class AgentSessionSendCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("send", "Start an asynchronous Workflow planning run") {
    private val id by argument("session-id")
    private val message by argument("message")
    private val repairs by option("--max-repair-attempts").int().default(2)
    override fun run() = command(root) { it.agentDocument("Planning run accepted", factory(root.taskServerConfig()).send(id, message, repairs)) }
}

class AgentRunStatusCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("status", "Show one Agent run") {
    private val id by argument("run-id")
    override fun run() = command(root) { it.agentDocument("Run", factory(root.taskServerConfig()).run(id)) }
}

class AgentRunCancelCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("cancel", "Request Agent run cancellation") {
    private val id by argument("run-id")
    override fun run() = command(root) { it.agentDocument("Run cancellation requested", factory(root.taskServerConfig()).cancelRun(id)) }
}

class AgentRunTimelineCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("timeline", "Show the Agent run audit timeline") {
    private val id by argument("run-id")
    override fun run() = command(root) { it.agentDocument("Run timeline", factory(root.taskServerConfig()).timeline(id)) }
}

class AgentRunProposalsCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("proposals", "List proposals created by a run") {
    private val id by argument("run-id")
    override fun run() = command(root) { it.agentDocument("Run proposals", factory(root.taskServerConfig()).proposals(id)) }
}

class AgentRunWatchCommand(
    private val root: EdbCommand,
    private val apiFactory: AgentFactory = ::AgentApiClient,
    private val streamFactory: (TaskServerConfig) -> AgentEventStreamClient = ::AgentEventStreamClient
) : DescribedCommand("watch", "Watch Agent lifecycle events over SSE") {
    private val sessionId by argument("session-id")
    private val runId by option("--run")
    private val keepOpen by option("--keep-open").flag()
    private val timeoutMs by option("--watch-timeout-ms").long()
    override fun run() = command(root) { output ->
        val config = root.taskServerConfig()
        val currentStatus = runId?.let { apiFactory(config).run(it).jsonObject["status"]?.jsonPrimitive?.content }
        output.agentWatchStarted(sessionId, runId, currentStatus)
        if (currentStatus in TERMINAL && !keepOpen) {
            output.agentWatchStopped(AgentWatchStopReason.ALREADY_TERMINAL); return@command
        }
        val reason = streamFactory(config).watch(sessionId, runId, runId != null && !keepOpen, timeoutMs, output::agentWatchEvent)
        output.agentWatchStopped(reason)
    }
    private companion object { val TERMINAL = setOf("COMPLETED", "FAILED", "CANCELLED") }
}

class AgentProposalInfoCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("info", "Show a Workflow proposal") {
    private val id by argument("proposal-id")
    override fun run() = command(root) { it.agentDocument("Proposal", factory(root.taskServerConfig()).proposal(id)) }
}
class AgentProposalApproveCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("approve", "Approve a valid Workflow proposal") {
    private val id by argument("proposal-id")
    override fun run() = command(root) { it.agentDocument("Proposal approved", factory(root.taskServerConfig()).approve(id)) }
}
class AgentProposalRejectCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("reject", "Reject a Workflow proposal") {
    private val id by argument("proposal-id"); private val reason by option("--reason").default("Rejected from CLI")
    override fun run() = command(root) { it.agentDocument("Proposal rejected", factory(root.taskServerConfig()).reject(id, reason)) }
}
class AgentProposalDecisionCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("decision", "Show the approval decision for a proposal") {
    private val id by argument("proposal-id")
    override fun run() = command(root) { it.agentDocument("Proposal decision", factory(root.taskServerConfig()).decision(id)) }
}
class AgentProposalSubmitCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("submit", "Persist and enqueue an approved Workflow proposal") {
    private val id by argument("proposal-id"); private val context by option("--context", "-c").multiple()
    override fun run() = command(root) { it.agentDocument("Proposal submitted", factory(root.taskServerConfig()).submit(id, context.associate(::entry))) }
    private fun entry(value: String): Pair<String, String> {
        val separator = value.indexOf('='); require(separator > 0) { "Context must use key=value: $value" }
        return value.substring(0, separator).trim().also { require(it.isNotEmpty()) } to value.substring(separator + 1)
    }
}

class AgentAdminProvidersCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("providers", "Show sanitized Provider configuration and passive health") {
    override fun run() = command(root) { it.agentDocument("Provider health", factory(root.taskServerConfig()).providerStatuses()) }
}
class AgentAdminModelMetricsCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("model-metrics", "Show per-profile model calls, Tokens and latency") {
    override fun run() = command(root) { it.agentDocument("Model metrics", factory(root.taskServerConfig()).modelMetrics()) }
}
class AgentAdminMetricsCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("metrics", "Show aggregate Agent run metrics") {
    override fun run() = command(root) { it.agentDocument("Agent metrics", factory(root.taskServerConfig()).metrics()) }
}
class AgentAdminRetentionShowCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("show", "Show Agent retention policy and manager state") {
    override fun run() = command(root) { it.agentDocument("Agent retention", factory(root.taskServerConfig()).retentionOverview()) }
}
class AgentAdminRetentionEstimateCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("estimate", "Estimate expired Agent runs") {
    override fun run() = command(root) { it.agentDocument("Agent retention estimate", factory(root.taskServerConfig()).retentionEstimate()) }
}
class AgentAdminRetentionCleanupCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("cleanup", "Permanently clean expired Agent runs") {
    private val confirmed by option("--confirm", help = "Confirm permanent Agent run deletion").flag()
    override fun run() = command(root) {
        require(confirmed) { "Agent retention cleanup is permanent. Re-run with --confirm." }
        it.agentDocument("Agent retention cleanup", factory(root.taskServerConfig()).retentionCleanup())
    }
}
class AgentAdminRetentionLastCommand(private val root: EdbCommand, private val factory: AgentFactory = ::AgentApiClient) :
    DescribedCommand("last", "Show the latest Agent retention cleanup result") {
    override fun run() = command(root) { it.agentDocument("Last Agent retention result", factory(root.taskServerConfig()).retentionLastResult()) }
}
