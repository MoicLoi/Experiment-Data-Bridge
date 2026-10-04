package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import me.moiclathy.edb.cli.service.TaskApiClient
import me.moiclathy.edb.cli.service.TaskServerConfig

class WorkerCommand : DescribedCommand(name = "worker", description = "Administer Docker-backed analysis workers") {
    override fun run() = Unit
}

class WorkerListCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "list", description = "List Docker workers and health state") {
    override fun run() = command(root) { it.workerFleet(clientFactory(root.taskServerConfig()).workerFleet()) }
}

class WorkerStatusCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "status", description = "Show one Docker worker") {
    private val workerId by argument("worker-id")

    override fun run() = command(root) { it.worker(clientFactory(root.taskServerConfig()).worker(workerId)) }
}

class WorkerStartCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "start", description = "Start the Docker worker fleet") {
    override fun run() = command(root) { it.workerFleet(clientFactory(root.taskServerConfig()).startWorkerFleet()) }
}

class WorkerStopCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "stop", description = "Stop the Docker worker fleet") {
    override fun run() = command(root) { it.workerFleet(clientFactory(root.taskServerConfig()).stopWorkerFleet()) }
}

class WorkerRestartCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "restart", description = "Restart one Docker worker") {
    private val workerId by argument("worker-id")

    override fun run() = command(root) { it.worker(clientFactory(root.taskServerConfig()).restartWorker(workerId)) }
}

class WorkerLogsCommand(
    private val root: EdbCommand,
    private val clientFactory: (TaskServerConfig) -> TaskApiClient = ::TaskApiClient
) : DescribedCommand(name = "logs", description = "Read or follow one Docker worker log") {
    private val workerId by argument("worker-id")
    private val tail by option("--tail", help = "Number of recent lines (1-10000)").int().default(200)
    private val timeoutMs by option("--timeout-ms", help = "Server log collection timeout").long().default(5_000)
    private val follow by option("--follow", help = "Poll and print appended log content until interrupted").flag()
    private val pollIntervalMs by option("--poll-interval-ms", help = "Follow polling interval").long().default(1_000)

    override fun run() = command(root) { output ->
        require(tail in 1..10_000) { "Log tail must be between 1 and 10000 lines." }
        require(timeoutMs > 0) { "Log timeout must be greater than zero." }
        require(pollIntervalMs > 0) { "Log poll interval must be greater than zero." }
        val client = clientFactory(root.taskServerConfig())
        if (!follow) {
            output.workerLogs(client.workerLogs(workerId, tail, timeoutMs))
        } else {
            var previous = ""
            while (!Thread.currentThread().isInterrupted) {
                val logs = client.workerLogs(workerId, tail, timeoutMs)
                val appended = appendedLogContent(previous, logs.content)
                if (appended.isNotEmpty()) output.workerLogChunk(workerId, logs.containerId, appended, logs.collectedAt)
                previous = logs.content
                try {
                    Thread.sleep(pollIntervalMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }
}

internal fun appendedLogContent(previous: String, current: String): String {
    if (previous.isEmpty()) return current
    if (current.startsWith(previous)) return current.substring(previous.length)
    val maximum = minOf(previous.length, current.length)
    val overlap = (maximum downTo 1).firstOrNull { size ->
        previous.regionMatches(previous.length - size, current, 0, size)
    } ?: 0
    return current.substring(overlap)
}
