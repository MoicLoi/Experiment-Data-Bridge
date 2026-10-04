package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import me.moiclathy.edb.cli.service.SubmitTaskRequest
import me.moiclathy.edb.cli.service.TaskApiClient
import me.moiclathy.edb.cli.service.TaskEventStreamClient
import me.moiclathy.edb.cli.service.TaskServerConfig
import me.moiclathy.edb.cli.service.TaskWatchStopReason

class TaskCommand : DescribedCommand(name = "task", description = "Control server-side tasks") {
    override fun run() = Unit
}

class TaskSubmitCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "submit", description = "Submit a Pipeline task") {
    private val projectId by argument("project-id").int()
    private val pipelineId by argument("pipeline-id")
    private val context by option("--context", "-c", help = "Pipeline context entry as key=value").multiple()
    private val priority by option("--priority", help = "Task priority from 0 to 100").int().default(50)
    private val maxAttempts by option("--max-attempts").int().default(1)
    private val initialDelayMs by option("--initial-delay-ms").long().default(1_000)
    private val backoffMultiplier by option("--backoff").double().default(2.0)
    private val maxDelayMs by option("--max-delay-ms").long().default(60_000)
    private val timeoutMs by option("--timeout-ms", help = "Single task execution timeout").long()

    override fun run() = command(root) { output ->
        val task = clientFactory(root.taskServerConfig()).submit(
            SubmitTaskRequest(
                projectId = projectId,
                pipelineId = pipelineId,
                context = context.associate(::parseContextEntry),
                priority = priority,
                maxAttempts = maxAttempts,
                initialDelayMs = initialDelayMs,
                backoffMultiplier = backoffMultiplier,
                maxDelayMs = maxDelayMs,
                timeoutMs = timeoutMs
            )
        )
        output.taskSubmitted(task)
    }

    private fun parseContextEntry(value: String): Pair<String, String> {
        val separator = value.indexOf('=')
        require(separator > 0) { "Context must use key=value: $value" }
        val key = value.substring(0, separator).trim()
        require(key.isNotEmpty()) { "Context key must not be blank." }
        return key to value.substring(separator + 1)
    }
}

class TaskListCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "list", description = "List tasks for a project") {
    private val projectId by argument("project-id").int()
    private val status by option("--status", help = "Filter by task status")
    private val page by option("--page").int().default(0)
    private val size by option("--size").int().default(20)

    override fun run() = command(root) { output ->
        output.taskList(clientFactory(root.taskServerConfig()).list(projectId, status, page, size))
    }
}

class TaskStatusCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "status", description = "Get one task status") {
    private val taskId by argument("task-id").int()

    override fun run() = command(root) { output ->
        output.taskStatus(clientFactory(root.taskServerConfig()).status(taskId))
    }
}

class TaskCancelCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "cancel", description = "Request cooperative task cancellation") {
    private val taskId by argument("task-id").int()

    override fun run() = command(root) { output ->
        output.taskCancelled(clientFactory(root.taskServerConfig()).cancel(taskId))
    }
}

class TaskTimelineCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "timeline", description = "Show the complete task audit timeline") {
    private val taskId by argument("task-id").int()

    override fun run() = command(root) { output ->
        output.taskTimeline(clientFactory(root.taskServerConfig()).timeline(taskId))
    }
}

class TaskRetryCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "retry", description = "Administratively requeue a failed terminal task") {
    private val taskId by argument("task-id").int()

    override fun run() = command(root) { output ->
        output.taskRetried(clientFactory(root.taskServerConfig()).requeue(taskId))
    }
}

class TaskWatchCommand(
    private val root: EdbCommand,
    private val taskClientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient,
    private val streamClientFactory: (TaskServerConfig) -> TaskEventStreamClient = ::TaskEventStreamClient
) : DescribedCommand(name = "watch", description = "Watch project task lifecycle events over SSE") {
    private val projectId by argument("project-id").int()
    private val taskId by option("--task", help = "Only watch one task ID").int()
    private val keepOpen by option("--keep-open", help = "Continue after the selected task reaches a terminal state").flag()
    private val watchTimeoutMs by option("--watch-timeout-ms", help = "Stop watching after this duration").long()

    override fun run() = command(root) { output ->
        val config = root.taskServerConfig()
        val snapshot = taskId?.let { id ->
            taskClientFactory(config).status(id).also {
                require(it.projectId == projectId) { "Task #$id belongs to project #${it.projectId}, not #$projectId." }
            }
        }
        output.taskWatchStarted(projectId, taskId, snapshot)
        if (snapshot?.status in TERMINAL_STATUSES && !keepOpen) {
            output.taskWatchStopped(TaskWatchStopReason.ALREADY_TERMINAL)
            return@command
        }
        val reason = streamClientFactory(config).watch(
            projectId = projectId,
            taskId = taskId,
            stopOnTerminal = taskId != null && !keepOpen,
            timeoutMs = watchTimeoutMs,
            onEvent = output::taskWatchEvent
        )
        output.taskWatchStopped(reason)
    }

    companion object {
        private val TERMINAL_STATUSES = setOf("COMPLETED", "FAILED", "TIMED_OUT", "CANCELLED")
    }
}
