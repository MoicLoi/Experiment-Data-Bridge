package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.int
import me.moiclathy.edb.cli.service.TaskApiClient
import me.moiclathy.edb.cli.service.TaskServerConfig

class SchedulerCommand : DescribedCommand(name = "scheduler", description = "Administer the server task scheduler") {
    override fun run() = Unit
}

class SchedulerStatusCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "status", description = "Show scheduler state") {
    override fun run() = command(root) { output ->
        output.schedulerState(clientFactory(root.taskServerConfig()).schedulerState())
    }
}

class SchedulerStartCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "start", description = "Start task workers") {
    override fun run() = command(root) { output ->
        output.schedulerState(clientFactory(root.taskServerConfig()).startScheduler())
    }
}

class SchedulerStopCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "stop", description = "Gracefully stop task workers") {
    override fun run() = command(root) { output ->
        output.schedulerState(clientFactory(root.taskServerConfig()).stopScheduler())
    }
}

class SchedulerWorkersCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "workers", description = "Set desired worker count") {
    private val workerCount by argument("count").int()

    override fun run() = command(root) { output ->
        output.schedulerState(clientFactory(root.taskServerConfig()).updateSchedulerWorkers(workerCount))
    }
}

class SchedulerMetricsCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "metrics", description = "Show task runtime metrics") {
    override fun run() = command(root) { output ->
        output.taskMetrics(clientFactory(root.taskServerConfig()).taskMetrics())
    }
}
