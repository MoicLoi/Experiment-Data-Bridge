package me.moiclathy.edb.cli.command

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.cli.service.PluginCliFailure
import me.moiclathy.edb.cli.service.PluginCliSummary
import me.moiclathy.edb.cli.service.TaskCliPage
import me.moiclathy.edb.cli.service.TaskCliSummary
import me.moiclathy.edb.cli.service.TaskCliTimeline
import me.moiclathy.edb.cli.service.TaskLifecycleCliEvent
import me.moiclathy.edb.cli.service.TaskWatchStopReason
import me.moiclathy.edb.cli.service.TaskSchedulerCliState
import me.moiclathy.edb.cli.service.TaskMetricsCliSnapshot
import me.moiclathy.edb.cli.service.TaskCleanupCliResult
import me.moiclathy.edb.cli.service.TaskRetentionCliEstimate
import me.moiclathy.edb.cli.service.TaskRetentionCliOverview
import me.moiclathy.edb.cli.service.TaskRetentionCliState
import me.moiclathy.edb.cli.service.DockerWorkerCliState
import me.moiclathy.edb.cli.service.DockerWorkerFleetCliState
import me.moiclathy.edb.cli.service.DockerWorkerLogsCliResult
import me.moiclathy.edb.cli.service.DataFileCliSummary
import me.moiclathy.edb.cli.service.DataFileDownloadResult
import me.moiclathy.edb.cli.service.AnalysisHandlerCli
import me.moiclathy.edb.cli.service.DataFileConversionCapabilityCli
import me.moiclathy.edb.cli.service.ReportDefinitionCli
import me.moiclathy.edb.cli.service.DataFileOrphanEstimateCli
import me.moiclathy.edb.cli.service.DataFileOrphanOverviewCli
import me.moiclathy.edb.cli.service.DataFileOrphanResultCli
import me.moiclathy.edb.cli.service.DataFileOrphanStateCli
import me.moiclathy.edb.cli.service.DataFileRetentionEstimateCli
import me.moiclathy.edb.cli.service.DataFileRetentionOverviewCli
import me.moiclathy.edb.cli.service.DataFileRetentionResultCli
import me.moiclathy.edb.cli.service.DataFileRetentionStateCli
import me.moiclathy.edb.cli.service.ProjectCliSummary
import me.moiclathy.edb.cli.service.ConfigurationValidationCliResult
import me.moiclathy.edb.cli.service.ProjectConfigurationDownloadResult
import me.moiclathy.edb.cli.service.ProjectConfigurationFileFormat
import me.moiclathy.edb.cli.service.ProjectConfigurationDiffCli
import me.moiclathy.edb.cli.service.ProjectConfigurationRevisionCli
import me.moiclathy.edb.cli.service.WorkflowCliSummary
import me.moiclathy.edb.cli.service.AdminUserCli
import me.moiclathy.edb.cli.service.ProjectMetadataCli
import me.moiclathy.edb.cli.service.ProjectTagsCli
import me.moiclathy.edb.cli.service.ProjectSnapshotCli
import me.moiclathy.edb.cli.service.SnapshotCompareCli
import me.moiclathy.edb.cli.service.SnapshotMergeCli
import me.moiclathy.edb.cli.service.TemplateCli
import me.moiclathy.edb.cli.service.TemplateCliSummary
import me.moiclathy.edb.cli.service.AgentLifecycleCliEvent
import me.moiclathy.edb.cli.service.AgentWatchStopReason

class MinecraftCliOutput(
    private val json: Boolean,
    private val stdout: (String) -> Unit,
    private val stderr: (String) -> Unit
) {
    fun agentDocument(action: String, document: JsonElement) {
        if (json) {
            stdout(encode(buildJsonObject { put("ok", true); put("result", document) }))
        } else {
            stdout("[OK] $action")
            AGENT_PRETTY_JSON.encodeToString(JsonElement.serializer(), document)
                .lineSequence().forEach { stdout("  $it") }
        }
    }

    fun agentWatchStarted(sessionId: String, runId: String?, status: String?) {
        if (json) stdout(encode(buildJsonObject {
            put("kind", "agent_watch_started"); put("sessionId", sessionId)
            runId?.let { put("runId", it) }; status?.let { put("status", it) }
        })) else stdout("[EDB] Watching Agent session $sessionId${runId?.let { " run $it" }.orEmpty()} (Ctrl+C to stop)")
    }

    fun agentWatchEvent(event: AgentLifecycleCliEvent) {
        if (json) stdout(encode(buildJsonObject {
            put("kind", "agent_event"); event.eventId?.let { put("eventId", it) }
            put("sessionId", event.sessionId); event.runId?.let { put("runId", it) }; put("projectId", event.projectId)
            put("type", event.type); event.runStatus?.let { put("runStatus", it) }
            put("detailsJson", event.detailsJson); put("occurredAt", event.occurredAt)
        })) else {
            stdout("[+] ${event.occurredAt} ${event.type}${event.runStatus?.let { " [$it]" }.orEmpty()}")
            if (event.detailsJson != "{}") stdout("    ${event.detailsJson}")
        }
    }

    fun agentWatchStopped(reason: AgentWatchStopReason) {
        if (json) stdout(encode(buildJsonObject { put("kind", "agent_watch_stopped"); put("reason", reason.name) }))
        else stdout("[EDB] Agent watch stopped: ${reason.name.lowercase().replace('_', ' ')}")
    }

    fun operatorChanged(user: AdminUserCli, granted: Boolean) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("operation", if (granted) "op" else "deop")
                put("user", buildJsonObject {
                    put("id", user.id)
                    put("username", user.username)
                    put("fullName", user.fullName)
                    user.email?.let { put("email", it) }
                    put("role", user.role)
                    put("active", user.active)
                })
            }))
        } else if (granted) {
            stdout("[OK] Made ${user.username} a server operator.")
            stdout("  The account must log in again to obtain an ADMIN token.")
        } else {
            stdout("[OK] Removed server operator privileges from ${user.username}.")
            stdout("  Existing tokens for the account are now invalid.")
        }
    }
    fun pluginList(plugins: List<PluginCliSummary>, failures: List<PluginCliFailure>) {
        if (json) {
            stdout(
                encode(
                    buildJsonObject {
                        put("ok", failures.isEmpty())
                        put("plugins", buildJsonArray { plugins.forEach { add(it.toJson()) } })
                        put("failures", buildJsonArray { failures.forEach { add(it.toJson()) } })
                    }
                )
            )
            return
        }
        stdout("[EDB] Found ${plugins.size} plugin(s).")
        plugins.forEach {
            stdout("[+] ${it.id} ${it.version} | tasks=${it.taskTypes.size} steps=${it.workflowStepTypes.size}")
        }
        failures.forEach { stderr("[!] ${it.jar.fileName} [${it.code}]: ${it.message}") }
    }

    fun pluginDetail(plugin: PluginCliSummary) {
        if (json) {
            stdout(encode(buildJsonObject { put("ok", true); put("plugin", plugin.toJson()) }))
        } else {
            stdout("[EDB] Plugin ${plugin.id}")
            stdout("  name: ${plugin.name}")
            stdout("  version: ${plugin.version}")
            stdout("  api: ${plugin.apiVersion}")
            stdout("  jar: ${plugin.jar}")
            stdout("  tasks: ${plugin.taskTypes.ifEmpty { listOf("-") }.joinToString()}")
            stdout("  steps: ${plugin.workflowStepTypes.ifEmpty { listOf("-") }.joinToString()}")
        }
    }

    fun pluginValidated(plugin: PluginCliSummary) {
        if (json) {
            stdout(encode(buildJsonObject { put("ok", true); put("plugin", plugin.toJson()) }))
        } else {
            stdout("[OK] Plugin JAR is valid: ${plugin.id} ${plugin.version} (API ${plugin.apiVersion})")
        }
    }

    fun workflowList(workflows: List<WorkflowCliSummary>) {
        if (json) {
            stdout(
                encode(
                    buildJsonObject {
                        put("ok", true)
                        put("workflows", buildJsonArray { workflows.forEach { add(it.toJson()) } })
                    }
                )
            )
        } else {
            stdout("[EDB] Found ${workflows.size} workflow(s).")
            workflows.forEach { stdout("[+] ${it.id} <- ${it.file.fileName}") }
        }
    }

    fun workflowValidated(workflow: WorkflowCliSummary) {
        if (json) {
            stdout(encode(buildJsonObject { put("ok", true); put("workflow", workflow.toJson()) }))
        } else {
            stdout("[OK] Workflow is valid: ${workflow.id} <- ${workflow.file}")
        }
    }

    fun taskSubmitted(task: TaskCliSummary) = taskResult("Submitted", task)

    fun taskStatus(task: TaskCliSummary) = taskResult("Task", task)

    fun taskCancelled(task: TaskCliSummary) = taskResult("Cancellation requested", task)

    fun taskRetried(task: TaskCliSummary) = taskResult("Task requeued", task)

    fun taskList(page: TaskCliPage) {
        if (json) {
            stdout(
                encode(
                    buildJsonObject {
                        put("ok", true)
                        put("page", page.page)
                        put("size", page.size)
                        put("totalElements", page.totalElements)
                        put("totalPages", page.totalPages)
                        put("tasks", buildJsonArray { page.items.forEach { add(it.toJson()) } })
                    }
                )
            )
        } else {
            stdout("[EDB] Tasks ${page.page + 1}/${page.totalPages.coerceAtLeast(1)} | total=${page.totalElements}")
            page.items.forEach {
                val progress = it.progress?.let { value -> " ${value.percent}%" }.orEmpty()
                stdout("[+] #${it.id} ${it.status}$progress | ${it.taskType} | priority=${it.priority}")
            }
        }
    }

    fun taskTimeline(timeline: TaskCliTimeline) {
        if (json) {
            stdout(
                encode(
                    buildJsonObject {
                        put("ok", true)
                        put("taskId", timeline.taskId)
                        put("events", buildJsonArray {
                            timeline.events.forEach { event ->
                                add(buildJsonObject {
                                    put("id", event.id)
                                    put("type", event.type)
                                    event.fromStatus?.let { put("fromStatus", it) }
                                    event.toStatus?.let { put("toStatus", it) }
                                    event.actorUserId?.let { put("actorUserId", it) }
                                    put("details", buildJsonObject {
                                        event.details.forEach { (key, value) -> put(key, value) }
                                    })
                                    put("createdAt", event.createdAt)
                                })
                            }
                        })
                    }
                )
            )
        } else {
            stdout("[EDB] Timeline for task #${timeline.taskId} (${timeline.events.size} event(s))")
            timeline.events.forEach { event ->
                val transition = when {
                    event.fromStatus != null || event.toStatus != null ->
                        " ${event.fromStatus ?: "-"} -> ${event.toStatus ?: "-"}"
                    else -> ""
                }
                stdout("[+] ${event.createdAt} ${event.type}$transition")
                if (event.details.isNotEmpty()) stdout("    ${event.details.entries.joinToString { "${it.key}=${it.value}" }}")
            }
        }
    }

    fun taskWatchStarted(projectId: Int, taskId: Int?, snapshot: TaskCliSummary?) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("kind", "watch_started")
                put("projectId", projectId)
                taskId?.let { put("taskId", it) }
                snapshot?.let { put("snapshot", it.toJson()) }
            }))
        } else {
            stdout("[EDB] Watching project #$projectId${taskId?.let { " task #$it" }.orEmpty()} (Ctrl+C to stop)")
            snapshot?.let { stdout("[=] Current task state: ${it.status}") }
        }
    }

    fun taskWatchEvent(event: TaskLifecycleCliEvent) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("kind", "task_event")
                event.eventId?.let { put("eventId", it) }
                put("taskId", event.taskId)
                put("projectId", event.projectId)
                put("type", event.type)
                event.previousStatus?.let { put("previousStatus", it) }
                put("status", event.status)
                put("attemptCount", event.attemptCount)
                put("details", buildJsonObject { event.details.forEach { (key, value) -> put(key, value) } })
                put("occurredAt", event.occurredAt)
            }))
        } else {
            val progress = event.details["progressPercent"]?.let { " $it%" }.orEmpty()
            stdout("[>] #${event.taskId} ${event.type} -> ${event.status}$progress | ${event.occurredAt}")
        }
    }

    fun taskWatchStopped(reason: TaskWatchStopReason) {
        if (json) stdout(encode(buildJsonObject { put("kind", "watch_stopped"); put("reason", reason.name) }))
        else stdout("[OK] Watch stopped: ${reason.name.lowercase().replace('_', ' ')}")
    }

    fun schedulerState(state: TaskSchedulerCliState) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("scheduler", buildJsonObject {
                    put("status", state.status)
                    put("desiredWorkerCount", state.desiredWorkerCount)
                    put("activeWorkerCount", state.activeWorkerCount)
                    state.startedAt?.let { put("startedAt", it) }
                    state.stoppedAt?.let { put("stoppedAt", it) }
                    state.lastFailure?.let { put("lastFailure", it) }
                })
            }))
        } else {
            stdout("[EDB] Scheduler: ${state.status}")
            stdout("  workers: ${state.activeWorkerCount}/${state.desiredWorkerCount}")
            state.startedAt?.let { stdout("  started: $it") }
            state.stoppedAt?.let { stdout("  stopped: $it") }
            state.lastFailure?.let { stderr("  last-failure: $it") }
        }
    }

    fun taskMetrics(metrics: TaskMetricsCliSnapshot) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("metrics", buildJsonObject {
                    put("queueDepth", metrics.queueDepth)
                    put("queuedCount", metrics.queuedCount)
                    put("retryingCount", metrics.retryingCount)
                    put("runningCount", metrics.runningCount)
                    put("succeededCount", metrics.succeededCount)
                    put("failedCount", metrics.failedCount)
                    put("timedOutCount", metrics.timedOutCount)
                    metrics.averageExecutionDurationMs?.let { put("averageExecutionDurationMs", it) }
                    put("collectedAt", metrics.collectedAt)
                })
            }))
        } else {
            stdout("[EDB] Task metrics at ${metrics.collectedAt}")
            stdout("  queue: depth=${metrics.queueDepth}, queued=${metrics.queuedCount}, retrying=${metrics.retryingCount}")
            stdout("  running: ${metrics.runningCount}")
            stdout("  terminal: success=${metrics.succeededCount}, failed=${metrics.failedCount}, timed-out=${metrics.timedOutCount}")
            stdout("  average-duration-ms: ${metrics.averageExecutionDurationMs ?: "-"}")
        }
    }

    fun workerFleet(fleet: DockerWorkerFleetCliState) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("fleet", buildJsonObject {
                    put("healthy", fleet.healthy)
                    put("observedAt", fleet.observedAt)
                    put("members", buildJsonArray { fleet.members.forEach { add(it.toJson()) } })
                })
            }))
        } else {
            stdout("[EDB] Docker Worker Fleet: ${if (fleet.healthy) "HEALTHY" else "DEGRADED"}")
            if (fleet.members.isEmpty()) stdout("  no managed workers configured")
            fleet.members.forEach { member ->
                stdout("  ${member.id}: ${member.status} | healthy=${member.healthy} | ${member.endpoint}")
                member.detail?.let { stderr("    detail: $it") }
            }
            stdout("  observed: ${fleet.observedAt}")
        }
    }

    fun worker(worker: DockerWorkerCliState) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("worker", worker.toJson()) }))
        else {
            stdout("[EDB] Worker ${worker.id}: ${worker.status}")
            stdout("  healthy: ${worker.healthy}")
            stdout("  container: ${worker.containerName}${worker.containerId?.let { " ($it)" }.orEmpty()}")
            stdout("  endpoint: ${worker.endpoint}")
            worker.detail?.let { stderr("  detail: $it") }
            stdout("  observed: ${worker.observedAt}")
        }
    }

    fun workerLogs(logs: DockerWorkerLogsCliResult) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("workerId", logs.workerId)
            put("containerId", logs.containerId)
            put("content", logs.content)
            put("collectedAt", logs.collectedAt)
        })) else {
            stdout("[EDB] Worker ${logs.workerId} logs (${logs.containerId})")
            if (logs.content.isNotEmpty()) stdout(logs.content.trimEnd())
            stdout("[OK] Collected at ${logs.collectedAt}")
        }
    }

    fun workerLogChunk(workerId: String, containerId: String, content: String, collectedAt: String) {
        if (json) stdout(encode(buildJsonObject {
            put("kind", "worker_log")
            put("workerId", workerId)
            put("containerId", containerId)
            put("content", content)
            put("collectedAt", collectedAt)
        })) else stdout(content.trimEnd())
    }

    private fun DockerWorkerCliState.toJson(): JsonElement = buildJsonObject {
        put("id", id)
        put("status", status)
        put("containerName", containerName)
        put("endpoint", endpoint)
        containerId?.let { put("containerId", it) }
        put("healthy", healthy)
        detail?.let { put("detail", it) }
        put("observedAt", observedAt)
    }

    fun retentionOverview(overview: TaskRetentionCliOverview) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("policy", buildJsonObject {
                    put("enabled", overview.policy.enabled)
                    put("retentionDays", overview.policy.retentionDays)
                    put("cleanupIntervalMinutes", overview.policy.cleanupIntervalMinutes)
                    put("batchSize", overview.policy.batchSize)
                    put("maxBatchesPerRun", overview.policy.maxBatchesPerRun)
                })
                put("state", overview.state.toJson())
            }))
        } else {
            stdout("[EDB] Task retention: ${if (overview.policy.enabled) "ENABLED" else "DISABLED"}")
            stdout("  policy: ${overview.policy.retentionDays} day(s), every ${overview.policy.cleanupIntervalMinutes} minute(s)")
            stdout("  batches: size=${overview.policy.batchSize}, max=${overview.policy.maxBatchesPerRun}")
            retentionStateLines(overview.state)
        }
    }

    fun retentionEstimate(estimate: TaskRetentionCliEstimate) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("estimate", buildJsonObject {
                    put("cutoff", estimate.cutoff)
                    put("expiredTasks", estimate.expiredTasks)
                    put("collectedAt", estimate.collectedAt)
                })
            }))
        } else {
            stdout("[EDB] Retention estimate: ${estimate.expiredTasks} task(s) eligible for deletion")
            stdout("  cutoff: ${estimate.cutoff}")
            stdout("  collected: ${estimate.collectedAt}")
        }
    }

    fun retentionCleanup(result: TaskCleanupCliResult) {
        if (json) {
            stdout(encode(buildJsonObject { put("ok", true); put("result", result.toJson()) }))
        } else {
            stdout("[OK] Retention cleanup permanently deleted ${result.deletedTasks} task(s).")
            stdout("  batches: ${result.completedBatches}")
            stdout("  cutoff: ${result.cutoff}")
            stdout("  completed: ${result.completedAt}")
        }
    }

    fun retentionState(state: TaskRetentionCliState) {
        if (json) {
            stdout(encode(buildJsonObject { put("ok", true); put("state", state.toJson()) }))
        } else {
            stdout("[EDB] Retention manager")
            retentionStateLines(state)
        }
    }

    fun dataFileUploaded(file: DataFileCliSummary) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("dataFile", file.toJson()) }))
        else {
            stdout("[OK] Uploaded DataFile #${file.id}: ${file.originalFileName}")
            dataFileLines(file)
        }
    }

    fun dataFileDeleted(file: DataFileCliSummary) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("dataFile", file.toJson()) }))
        else stdout("[OK] DataFile #${file.id} is ${file.status}; stored content cleanup was requested.")
    }

    fun dataFileDetail(file: DataFileCliSummary) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("dataFile", file.toJson()) }))
        else {
            stdout("[EDB] DataFile #${file.id}: ${file.originalFileName}")
            dataFileLines(file)
            if (file.metadata.isNotEmpty()) stdout("  metadata: ${file.metadata}")
        }
    }

    fun dataFileList(files: List<DataFileCliSummary>) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("dataFiles", buildJsonArray { files.forEach { add(it.toJson()) } })
            }))
        } else {
            stdout("[EDB] Found ${files.size} data file(s).")
            files.forEach {
                stdout("[+] #${it.id} ${it.originalFileName} | ${it.kind}/${it.format} | ${it.status} | ${formatBytes(it.sizeBytes)}")
            }
        }
    }

    fun dataFileDownloaded(result: DataFileDownloadResult) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("path", result.path.toString())
                put("bytesWritten", result.bytesWritten)
                result.contentType?.let { put("contentType", it) }
            }))
        } else {
            stdout("[OK] Downloaded ${result.bytesWritten} byte(s) to ${result.path}")
        }
    }

    fun dataFileArtifacts(kind: String, files: List<DataFileCliSummary>) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("kind", kind)
                put("artifacts", buildJsonArray { files.forEach { add(it.toJson()) } })
            }))
        } else {
            stdout("[EDB] Found ${files.size} $kind artifact(s).")
            files.forEach {
                stdout("[+] #${it.id} ${it.originalFileName} | ${it.format}/${it.status} | task=${it.producingTaskId ?: "-"}")
            }
        }
    }

    fun conversionTargets(capabilities: List<DataFileConversionCapabilityCli>) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("targets", buildJsonArray {
                    capabilities.forEach { capability ->
                        add(buildJsonObject {
                            put("sourceFormat", capability.sourceFormat)
                            put("targetFormat", capability.targetFormat)
                            put("options", buildJsonArray {
                                capability.options.forEach { option ->
                                    add(buildJsonObject {
                                        put("name", option.name)
                                        put("type", option.type)
                                        put("required", option.required)
                                        option.defaultValue?.let { put("defaultValue", it) }
                                        option.description?.let { put("description", it) }
                                    })
                                }
                            })
                        })
                    }
                })
            }))
        } else {
            stdout("[EDB] Found ${capabilities.size} conversion target(s).")
            capabilities.forEach { capability ->
                stdout("[+] ${capability.sourceFormat} -> ${capability.targetFormat}")
                capability.options.forEach { option ->
                    val requirement = if (option.required) "required" else "optional"
                    val fallback = option.defaultValue?.let { ", default=$it" }.orEmpty()
                    stdout("    ${option.name}: ${option.type} ($requirement$fallback)")
                }
            }
        }
    }

    fun analysisHandlers(handlers: List<AnalysisHandlerCli>) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("handlers", buildJsonArray {
                    handlers.forEach { handler ->
                        add(buildJsonObject {
                            put("id", handler.id)
                            put("analysisType", handler.analysisType)
                            put("tool", handler.tool)
                            put("outputFormat", handler.outputFormat)
                        })
                    }
                })
            }))
        } else {
            stdout("[EDB] Found ${handlers.size} analysis handler(s).")
            handlers.forEach { stdout("[+] ${it.id} | ${it.analysisType} via ${it.tool} -> ${it.outputFormat}") }
        }
    }

    fun reportDefinitions(definitions: List<ReportDefinitionCli>) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("definitions", buildJsonArray {
                    definitions.forEach { definition ->
                        add(buildJsonObject {
                            put("id", definition.id)
                            put("version", definition.version)
                            put("title", definition.title)
                            put("generatorId", definition.generatorId)
                            put("outputFormat", definition.outputFormat)
                            put("outputFileName", definition.outputFileName)
                            put("defaultParameters", buildJsonObject {
                                definition.defaultParameters.forEach { (key, value) -> put(key, value) }
                            })
                        })
                    }
                })
            }))
        } else {
            stdout("[EDB] Found ${definitions.size} report definition(s).")
            definitions.forEach {
                stdout("[+] ${it.id}@${it.version} | ${it.title} | ${it.generatorId} -> ${it.outputFormat}/${it.outputFileName}")
            }
        }
    }

    fun orphanOverview(overview: DataFileOrphanOverviewCli) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("policy", buildJsonObject {
                put("enabled", overview.policy.enabled)
                put("minimumAgeHours", overview.policy.minimumAgeHours)
                put("cleanupIntervalMinutes", overview.policy.cleanupIntervalMinutes)
                put("maxDeletesPerRun", overview.policy.maxDeletesPerRun)
            })
            put("state", overview.state.toJson())
        })) else {
            stdout("[EDB] DataFile orphan cleanup: ${if (overview.policy.enabled) "ENABLED" else "DISABLED"}")
            stdout("  policy: minimum-age=${overview.policy.minimumAgeHours}h, interval=${overview.policy.cleanupIntervalMinutes}m, max-delete=${overview.policy.maxDeletesPerRun}")
            orphanStateLines(overview.state)
        }
    }

    fun orphanEstimate(estimate: DataFileOrphanEstimateCli) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("estimate", buildJsonObject {
                put("cutoff", estimate.cutoff)
                put("scannedFiles", estimate.scannedFiles)
                put("orphanFiles", estimate.orphanFiles)
                put("orphanBytes", estimate.orphanBytes)
                put("collectedAt", estimate.collectedAt)
            })
        })) else {
            stdout("[EDB] Orphan estimate: ${estimate.orphanFiles}/${estimate.scannedFiles} file(s), ${formatBytes(estimate.orphanBytes)}")
            stdout("  cutoff: ${estimate.cutoff}")
            stdout("  collected: ${estimate.collectedAt}")
        }
    }

    fun orphanCleanup(result: DataFileOrphanResultCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("result", result.toJson()) }))
        else {
            stdout("[OK] Orphan cleanup deleted ${result.deletedFiles} file(s), ${formatBytes(result.deletedBytes)}; failures=${result.failures}")
            stdout("  scanned/candidates: ${result.scannedFiles}/${result.orphanCandidates}")
            stdout("  completed: ${result.completedAt}")
        }
    }

    fun orphanState(state: DataFileOrphanStateCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("state", state.toJson()) }))
        else {
            stdout("[EDB] DataFile orphan cleanup manager")
            orphanStateLines(state)
        }
    }

    fun dataFileRetentionOverview(overview: DataFileRetentionOverviewCli) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("policy", buildJsonObject {
                put("enabled", overview.policy.enabled)
                put("retentionDays", overview.policy.retentionDays)
                put("cleanupIntervalMinutes", overview.policy.cleanupIntervalMinutes)
                put("batchSize", overview.policy.batchSize)
                put("maxBatchesPerRun", overview.policy.maxBatchesPerRun)
            })
            put("state", overview.state.toJson())
        })) else {
            stdout("[EDB] DataFile record retention: ${if (overview.policy.enabled) "ENABLED" else "DISABLED"}")
            stdout("  policy: ${overview.policy.retentionDays} day(s), interval=${overview.policy.cleanupIntervalMinutes}m")
            stdout("  batches: size=${overview.policy.batchSize}, max=${overview.policy.maxBatchesPerRun}")
            dataFileRetentionStateLines(overview.state)
        }
    }

    fun dataFileRetentionEstimate(estimate: DataFileRetentionEstimateCli) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("estimate", buildJsonObject {
                put("cutoff", estimate.cutoff)
                put("expiredDeletedFiles", estimate.expiredDeletedFiles)
                put("collectedAt", estimate.collectedAt)
            })
        })) else {
            stdout("[EDB] DataFile retention estimate: ${estimate.expiredDeletedFiles} deleted record(s)")
            stdout("  cutoff: ${estimate.cutoff}")
        }
    }

    fun dataFileRetentionCleanup(result: DataFileRetentionResultCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("result", result.toJson()) }))
        else stdout("[OK] DataFile retention removed ${result.deletedRecords} record(s) in ${result.completedBatches} batch(es).")
    }

    fun dataFileRetentionState(state: DataFileRetentionStateCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("state", state.toJson()) }))
        else {
            stdout("[EDB] DataFile retention manager")
            dataFileRetentionStateLines(state)
        }
    }

    fun projectList(projects: List<ProjectCliSummary>) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("projects", buildJsonArray { projects.forEach { add(it.toJson()) } })
        })) else {
            stdout("[EDB] Found ${projects.size} project(s).")
            projects.forEach { stdout("[+] #${it.id} ${it.name} | ${it.mode}") }
        }
    }

    fun projectDetail(project: ProjectCliSummary) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("project", project.toJson()) }))
        else {
            stdout("[EDB] Project #${project.id}: ${project.name}")
            stdout("  mode: ${project.mode}")
            stdout("  owner: #${project.ownerId}")
            project.description?.let { stdout("  description: $it") }
            stdout("  configuration: ${if (project.configYaml == null) "not initialized" else "initialized"}")
        }
    }

    fun projectCreated(project: ProjectCliSummary) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("project", project.toJson()) }))
        else stdout("[OK] Created project #${project.id}: ${project.name} (${project.mode})")
    }

    fun projectUpdated(project: ProjectCliSummary) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("project", project.toJson()) }))
        else {
            stdout("[OK] Updated project #${project.id}: ${project.name}")
            stdout("  mode: ${project.mode}")
            project.description?.let { stdout("  description: $it") }
        }
    }

    fun projectReloaded(projectId: Int) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("projectId", projectId)
            put("reloaded", true)
        })) else stdout("[OK] Reloaded project #$projectId.")
    }

    fun projectMetadata(metadata: ProjectMetadataCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("metadata", metadata.toJson()) }))
        else {
            stdout("[EDB] Metadata for project #${metadata.projectId}")
            stdout("  description: ${metadata.description ?: "-"}")
            stdout("  category: ${metadata.category ?: "-"}")
            stdout("  data-source: ${metadata.dataSource ?: "-"}")
            stdout("  tags: ${metadata.tags.ifEmpty { listOf("-") }.joinToString(", ")}")
            stdout("  updated: ${metadata.updatedAt}")
        }
    }

    fun projectMetadataSet(metadata: ProjectMetadataCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("metadata", metadata.toJson()) }))
        else stdout("[OK] Saved metadata for project #${metadata.projectId}.")
    }

    fun projectMetadataDeleted(projectId: Int) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("projectId", projectId); put("deleted", true) }))
        else stdout("[OK] Deleted metadata for project #$projectId.")
    }

    fun projectTags(result: ProjectTagsCli) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("projectId", result.projectId)
            put("tags", buildJsonArray { result.tags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        })) else {
            stdout("[EDB] ${result.tags.size} tag(s) for project #${result.projectId}")
            result.tags.forEach { stdout("[+] $it") }
        }
    }

    private fun ProjectMetadataCli.toJson(): JsonElement = buildJsonObject {
        put("projectId", projectId)
        description?.let { put("description", it) }
        category?.let { put("category", it) }
        put("tags", buildJsonArray { tags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        put("updatedAt", updatedAt)
        dataSource?.let { put("dataSource", it) }
    }

    fun snapshotCreated(snapshot: ProjectSnapshotCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("snapshot", snapshot.toJson()) }))
        else stdout("[OK] Created snapshot #${snapshot.id}, version ${snapshot.version}, for project #${snapshot.projectId}.")
    }

    fun snapshotList(snapshots: List<ProjectSnapshotCli>) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("snapshots", buildJsonArray { snapshots.forEach { add(it.toJson()) } })
        })) else {
            stdout("[EDB] Found ${snapshots.size} snapshot(s).")
            snapshots.forEach {
                stdout("[+] #${it.id} v${it.version} | ${it.createdAt} | ${it.createdBy}${it.description?.let { value -> " | $value" }.orEmpty()}")
            }
        }
    }

    fun snapshotDetail(snapshot: ProjectSnapshotCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("snapshot", snapshot.toJson()) }))
        else {
            stdout("[EDB] Snapshot #${snapshot.id}, version ${snapshot.version}")
            stdout("  project: #${snapshot.projectId}")
            stdout("  created: ${snapshot.createdAt} by ${snapshot.createdBy}")
            snapshot.description?.let { stdout("  description: $it") }
            stdout("--- configuration")
            stdout(snapshot.yaml)
        }
    }

    fun snapshotDeleted(snapshotId: Long) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("snapshotId", snapshotId); put("deleted", true) }))
        else stdout("[OK] Deleted snapshot #$snapshotId.")
    }

    fun snapshotCompare(result: SnapshotCompareCli) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("comparison", buildJsonObject {
                put("sourceSnapshotId", result.sourceSnapshotId)
                put("targetSnapshotId", result.targetSnapshotId)
                put("sourceVersion", result.sourceVersion)
                put("targetVersion", result.targetVersion)
                put("identical", result.identical)
                put("changes", buildJsonArray { result.changes.forEach { change -> add(buildJsonObject {
                    put("path", change.path)
                    put("type", change.type)
                    change.sourceValue?.let { put("sourceValue", it) }
                    change.targetValue?.let { put("targetValue", it) }
                }) } })
            })
        })) else {
            stdout("[EDB] Snapshot comparison #${result.sourceSnapshotId} -> #${result.targetSnapshotId}")
            stdout("  identical: ${result.identical}")
            result.changes.forEach { stdout("[${it.type}] ${it.path}: ${it.sourceValue ?: "null"} -> ${it.targetValue ?: "null"}") }
        }
    }

    fun snapshotMerge(result: SnapshotMergeCli) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", result.success)
            put("targetSnapshotId", result.targetSnapshotId)
            put("sourceSnapshotId", result.sourceSnapshotId)
            result.mergedYaml?.let { put("mergedYaml", it) }
            put("conflicts", buildJsonArray { result.conflicts.forEach { conflict -> add(buildJsonObject {
                put("path", conflict.path)
                conflict.baseValue?.let { put("baseValue", it) }
                conflict.sourceValue?.let { put("sourceValue", it) }
                conflict.targetValue?.let { put("targetValue", it) }
                put("reason", conflict.reason)
            }) } })
        })) else if (result.success) {
            stdout("[OK] Merged snapshot #${result.sourceSnapshotId} into #${result.targetSnapshotId}.")
            stdout(result.mergedYaml!!)
        } else {
            stderr("[ERROR] Snapshot merge has ${result.conflicts.size} conflict(s).")
            result.conflicts.forEach { stderr("  ${it.path}: ${it.reason}") }
        }
    }

    private fun ProjectSnapshotCli.toJson(): JsonElement = buildJsonObject {
        put("id", id)
        put("projectId", projectId)
        put("version", version)
        put("createdAt", createdAt)
        put("createdBy", createdBy)
        description?.let { put("description", it) }
        put("yaml", yaml)
    }

    fun templateList(templates: List<TemplateCliSummary>) {
        if (json) stdout(encode(buildJsonObject {
            put("ok", true)
            put("templates", buildJsonArray { templates.forEach { add(buildJsonObject {
                put("id", it.id); put("name", it.name); put("type", it.type); put("visibility", it.visibility)
            }) } })
        })) else {
            stdout("[EDB] Found ${templates.size} visible template(s).")
            templates.forEach { stdout("[+] #${it.id} ${it.name} | ${it.type} | ${it.visibility}") }
        }
    }

    fun templateDetail(template: TemplateCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("template", template.toJson()) }))
        else {
            stdout("[EDB] Template #${template.id}: ${template.name}")
            stdout("  type: ${template.type}")
            stdout("  visibility: ${template.visibility}")
            stdout("  author: ${template.author}")
            stdout("  version: ${template.version}")
            if (template.description.isNotBlank()) stdout("  description: ${template.description}")
            stdout("--- template")
            stdout(template.yaml)
        }
    }

    fun templateCreated(template: TemplateCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("template", template.toJson()) }))
        else stdout("[OK] Created template #${template.id}: ${template.name}.")
    }

    fun templateUpdated(template: TemplateCli) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("template", template.toJson()) }))
        else stdout("[OK] Updated template #${template.id}: ${template.name}, version ${template.version}.")
    }

    fun templateDeleted(id: Long) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("templateId", id); put("deleted", true) }))
        else stdout("[OK] Deleted template #$id.")
    }

    private fun TemplateCli.toJson(): JsonElement = buildJsonObject {
        put("id", id); put("name", name); put("description", description); put("type", type)
        put("visibility", visibility); put("createdAt", createdAt); put("updatedAt", updatedAt)
        put("author", author); put("version", version); put("yaml", yaml)
    }

    fun projectDeleted(projectId: Int) {
        if (json) stdout(encode(buildJsonObject { put("ok", true); put("projectId", projectId); put("deleted", true) }))
        else stdout("[OK] Permanently deleted project #$projectId.")
    }

    fun projectConfiguration(projectId: Int, yaml: String) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("projectId", projectId)
                put("yaml", yaml)
            }))
        } else {
            stdout("[EDB] Configuration for project #$projectId")
            stdout(yaml)
        }
    }

    fun configurationValidation(result: ConfigurationValidationCliResult) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", result.valid)
                put("valid", result.valid)
                put("errors", buildJsonArray {
                    result.errors.forEach { error ->
                        add(buildJsonObject {
                            put("field", error.field)
                            put("message", error.message)
                        })
                    }
                })
            }))
        } else if (result.valid) {
            stdout("[OK] Configuration is valid.")
        } else {
            stderr("[ERROR] Configuration is invalid (${result.errors.size} error(s)).")
            result.errors.forEach { stderr("  ${it.field}: ${it.message}") }
        }
    }

    fun configurationMutation(action: String, result: JsonObject) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("action", action)
                put("result", result)
            }))
        } else {
            stdout("[OK] Project configuration $action.")
        }
    }

    fun configurationExported(
        result: ProjectConfigurationDownloadResult,
        format: ProjectConfigurationFileFormat
    ) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("format", format.name.lowercase())
                put("path", result.path.toString())
                put("bytes", result.bytesWritten)
                result.contentType?.let { put("contentType", it) }
            }))
        } else {
            stdout("[OK] Exported ${format.name.lowercase()} configuration to ${result.path} (${result.bytesWritten} bytes).")
        }
    }

    fun configurationHistory(revisions: List<ProjectConfigurationRevisionCli>) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("revisions", buildJsonArray { revisions.forEach { add(it.toJson()) } })
            }))
        } else {
            stdout("[EDB] Found ${revisions.size} configuration revision(s).")
            revisions.forEach {
                stdout("[+] ${it.shortCommitId} | ${it.time} | ${it.author} <${it.email}>")
                stdout("    ${it.message}")
            }
        }
    }

    fun configurationDiff(diff: ProjectConfigurationDiffCli) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("diff", buildJsonObject {
                    put("oldCommit", diff.oldCommit)
                    put("newCommit", diff.newCommit)
                    put("oldContent", diff.oldContent)
                    put("newContent", diff.newContent)
                })
            }))
        } else {
            stdout("[EDB] Configuration diff ${diff.oldCommit} -> ${diff.newCommit}")
            stdout("--- ${diff.oldCommit}")
            stdout(diff.oldContent)
            stdout("+++ ${diff.newCommit}")
            stdout(diff.newContent)
        }
    }

    fun configurationRestored(revision: ProjectConfigurationRevisionCli) {
        if (json) {
            stdout(encode(buildJsonObject {
                put("ok", true)
                put("revision", revision.toJson())
            }))
        } else {
            stdout("[OK] Restored configuration in commit ${revision.shortCommitId}.")
            stdout("  ${revision.message}")
        }
    }

    private fun ProjectConfigurationRevisionCli.toJson(): JsonElement = buildJsonObject {
        put("commitId", commitId)
        put("shortCommitId", shortCommitId)
        put("message", message)
        put("author", author)
        put("email", email)
        put("time", time)
    }

    private fun ProjectCliSummary.toJson(): JsonElement = buildJsonObject {
        put("id", id)
        put("name", name)
        description?.let { put("description", it) }
        put("ownerId", ownerId)
        put("mode", mode)
        configYaml?.let { put("configYaml", it) }
    }

    private fun orphanStateLines(state: DataFileOrphanStateCli) {
        stdout("  running: ${state.running}")
        state.lastStartedAt?.let { stdout("  last-started: $it") }
        state.lastResult?.let { stdout("  last-result: deleted=${it.deletedFiles}, bytes=${it.deletedBytes}, failures=${it.failures}") }
        state.lastFailure?.let { stderr("  last-failure: $it") }
    }

    private fun dataFileRetentionStateLines(state: DataFileRetentionStateCli) {
        stdout("  running: ${state.running}")
        state.lastStartedAt?.let { stdout("  last-started: $it") }
        state.lastResult?.let { stdout("  last-result: deleted-records=${it.deletedRecords}, batches=${it.completedBatches}") }
        state.lastFailure?.let { stderr("  last-failure: $it") }
    }

    private fun DataFileOrphanStateCli.toJson(): JsonElement = buildJsonObject {
        put("running", running)
        lastStartedAt?.let { put("lastStartedAt", it) }
        lastResult?.let { put("lastResult", it.toJson()) }
        lastFailure?.let { put("lastFailure", it) }
    }

    private fun DataFileOrphanResultCli.toJson(): JsonElement = buildJsonObject {
        put("cutoff", cutoff); put("scannedFiles", scannedFiles); put("orphanCandidates", orphanCandidates)
        put("deletedFiles", deletedFiles); put("deletedBytes", deletedBytes); put("failures", failures); put("completedAt", completedAt)
    }

    private fun DataFileRetentionStateCli.toJson(): JsonElement = buildJsonObject {
        put("running", running)
        lastStartedAt?.let { put("lastStartedAt", it) }
        lastResult?.let { put("lastResult", it.toJson()) }
        lastFailure?.let { put("lastFailure", it) }
    }

    private fun DataFileRetentionResultCli.toJson(): JsonElement = buildJsonObject {
        put("cutoff", cutoff); put("deletedRecords", deletedRecords); put("completedBatches", completedBatches); put("completedAt", completedAt)
    }

    private fun dataFileLines(file: DataFileCliSummary) {
        stdout("  project: ${file.projectId}")
        stdout("  kind/format/status: ${file.kind}/${file.format}/${file.status}")
        stdout("  size: ${formatBytes(file.sizeBytes)}")
        file.mediaType?.let { stdout("  media-type: $it") }
        file.sha256?.let { stdout("  sha256: $it") }
        file.producingTaskId?.let { stdout("  producing-task: #$it") }
        if (file.parentDataFileIds.isNotEmpty()) stdout("  parents: ${file.parentDataFileIds.joinToString()}")
        stdout("  updated: ${file.updatedAt}")
    }

    private fun formatBytes(value: Long?): String = when {
        value == null -> "-"
        value < 1_024 -> "$value B"
        value < 1_048_576 -> "%.1f KiB".format(value / 1_024.0)
        value < 1_073_741_824 -> "%.1f MiB".format(value / 1_048_576.0)
        else -> "%.1f GiB".format(value / 1_073_741_824.0)
    }

    private fun DataFileCliSummary.toJson(): JsonElement = buildJsonObject {
        put("id", id)
        put("projectId", projectId)
        put("createdByUserId", createdByUserId)
        put("kind", kind)
        put("format", format)
        put("status", status)
        put("originalFileName", originalFileName)
        mediaType?.let { put("mediaType", it) }
        sizeBytes?.let { put("sizeBytes", it) }
        sha256?.let { put("sha256", it) }
        storageProvider?.let { put("storageProvider", it) }
        put("origin", origin)
        put("parentDataFileIds", buildJsonArray {
            parentDataFileIds.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
        })
        producingTaskId?.let { put("producingTaskId", it) }
        externalSystem?.let { put("externalSystem", it) }
        put("metadata", metadata)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun retentionStateLines(state: TaskRetentionCliState) {
        stdout("  running: ${state.running}")
        state.lastStartedAt?.let { stdout("  last-started: $it") }
        state.lastResult?.let {
            stdout("  last-result: deleted=${it.deletedTasks}, batches=${it.completedBatches}, completed=${it.completedAt}")
        }
        state.lastFailure?.let { stderr("  last-failure: $it") }
    }

    private fun TaskRetentionCliState.toJson(): JsonElement = buildJsonObject {
        put("running", running)
        lastStartedAt?.let { put("lastStartedAt", it) }
        lastResult?.let { put("lastResult", it.toJson()) }
        lastFailure?.let { put("lastFailure", it) }
    }

    private fun TaskCleanupCliResult.toJson(): JsonElement = buildJsonObject {
        put("cutoff", cutoff)
        put("deletedTasks", deletedTasks)
        put("completedBatches", completedBatches)
        put("completedAt", completedAt)
    }

    fun error(message: String) {
        if (json) stderr(encode(buildJsonObject { put("ok", false); put("error", message) }))
        else stderr("[ERROR] $message")
    }

    private fun PluginCliSummary.toJson(): JsonElement = buildJsonObject {
        put("id", id)
        put("name", name)
        put("version", version)
        put("apiVersion", apiVersion)
        put("jar", jar.toString())
        put("taskTypes", JsonArray(taskTypes.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        put("workflowStepTypes", JsonArray(workflowStepTypes.map { kotlinx.serialization.json.JsonPrimitive(it) }))
    }

    private fun PluginCliFailure.toJson(): JsonElement = buildJsonObject {
        put("jar", jar.toString())
        put("code", code.name)
        put("message", message)
    }

    private fun WorkflowCliSummary.toJson(): JsonElement = buildJsonObject {
        put("id", id)
        put("file", file.toString())
    }

    private fun taskResult(action: String, task: TaskCliSummary) {
        if (json) {
            stdout(encode(buildJsonObject { put("ok", true); put("task", task.toJson()) }))
        } else {
            stdout("[OK] $action #${task.id}: ${task.status}")
            stdout("  project: ${task.projectId}")
            stdout("  type: ${task.taskType}")
            stdout("  priority: ${task.priority}")
            stdout("  attempts: ${task.attemptCount}/${task.maxAttempts}")
            task.progress?.let { stdout("  progress: ${it.percent}%${it.message?.let { message -> " - $message" }.orEmpty()}") }
            task.result?.let { stdout("  result: ${it.outputPath ?: it.metadata.ifEmpty { mapOf("status" to "complete") }}") }
            task.error?.let { stderr("  error: [${it.category}/${it.code}] ${it.message}") }
            task.cancellationRequestedAt?.let { stdout("  cancellation-requested-at: $it") }
            stdout("  updated: ${task.updatedAt}")
        }
    }

    private fun TaskCliSummary.toJson(): JsonElement = buildJsonObject {
        put("id", id)
        put("projectId", projectId)
        put("taskType", taskType)
        put("status", status)
        put("priority", priority)
        put("attemptCount", attemptCount)
        put("maxAttempts", maxAttempts)
        nextAttemptAt?.let { put("nextAttemptAt", it) }
        timeoutMs?.let { put("timeoutMs", it) }
        cancellationRequestedAt?.let { put("cancellationRequestedAt", it) }
        cancellationRequestedBy?.let { put("cancellationRequestedBy", it) }
        progress?.let { value ->
            put("progress", buildJsonObject {
                put("percent", value.percent)
                value.message?.let { put("message", it) }
                put("updatedAt", value.updatedAt)
            })
        }
        result?.let { value ->
            put("result", buildJsonObject {
                value.outputPath?.let { put("outputPath", it) }
                value.contentType?.let { put("contentType", it) }
                put("metadata", buildJsonObject { value.metadata.forEach { (key, item) -> put(key, item) } })
            })
        }
        error?.let { value ->
            put("error", buildJsonObject {
                put("code", value.code)
                put("category", value.category)
                put("message", value.message)
                put("retryable", value.retryable)
                put("details", buildJsonObject { value.details.forEach { (key, item) -> put(key, item) } })
                put("occurredAt", value.occurredAt)
            })
        }
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        startedAt?.let { put("startedAt", it) }
        completedAt?.let { put("completedAt", it) }
    }

    private fun encode(element: JsonElement): String = Json.encodeToString(JsonElement.serializer(), element)

    private companion object {
        val AGENT_PRETTY_JSON = Json { prettyPrint = true }
    }
}
