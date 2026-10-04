package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TaskApiClient(
    private val config: TaskServerConfig,
    private val transport: TaskHttpTransport = JdkTaskHttpTransport()
) {
    fun submit(request: SubmitTaskRequest): TaskCliSummary {
        require(request.projectId > 0) { "Project ID must be greater than zero." }
        require(request.pipelineId.isNotBlank()) { "Pipeline ID must not be blank." }
        require(request.priority in 0..100) { "Priority must be between 0 and 100." }
        require(request.maxAttempts > 0) { "Maximum attempts must be greater than zero." }
        require(request.initialDelayMs >= 0 && request.maxDelayMs >= 0) { "Retry delays cannot be negative." }
        require(request.backoffMultiplier >= 1.0) { "Backoff multiplier must be at least 1.0." }
        require(request.timeoutMs == null || request.timeoutMs > 0) { "Task timeout must be greater than zero." }
        return request("POST", "/api/v1/tasks/pipeline", request.toJson()).toTask()
    }

    fun list(projectId: Int, status: String?, page: Int, size: Int): TaskCliPage {
        require(projectId > 0) { "Project ID must be greater than zero." }
        require(page >= 0) { "Page must not be negative." }
        require(size in 1..100) { "Page size must be between 1 and 100." }
        val query = buildList {
            status?.takeIf { it.isNotBlank() }?.let { add("status=${encode(it.uppercase())}") }
            add("page=$page")
            add("size=$size")
        }.joinToString("&")
        val json = request("GET", "/api/v1/projects/$projectId/tasks?$query").jsonObject
        return TaskCliPage(
            items = json.required("items").jsonArray.map { it.jsonObject.toTask() },
            page = json.int("page"),
            size = json.int("size"),
            totalElements = json.long("totalElements"),
            totalPages = json.long("totalPages")
        )
    }

    fun status(taskId: Int): TaskCliSummary {
        require(taskId > 0) { "Task ID must be greater than zero." }
        return request("GET", "/api/v1/tasks/$taskId").toTask()
    }

    fun cancel(taskId: Int): TaskCliSummary {
        require(taskId > 0) { "Task ID must be greater than zero." }
        return request("POST", "/api/v1/tasks/$taskId/cancel").toTask()
    }

    fun timeline(taskId: Int): TaskCliTimeline {
        require(taskId > 0) { "Task ID must be greater than zero." }
        val json = request("GET", "/api/v1/tasks/$taskId/timeline")
        return TaskCliTimeline(
            taskId = json.int("taskId"),
            events = json.required("events").jsonArray.map { element ->
                val event = element.jsonObject
                TaskCliAuditEvent(
                    id = event.long("id"),
                    type = event.string("type"),
                    fromStatus = event.nullableString("fromStatus"),
                    toStatus = event.nullableString("toStatus"),
                    actorUserId = event.nullableInt("actorUserId"),
                    details = event.stringMap("details"),
                    createdAt = event.string("createdAt")
                )
            }
        )
    }

    fun requeue(taskId: Int): TaskCliSummary {
        require(taskId > 0) { "Task ID must be greater than zero." }
        return request("POST", "/api/v1/admin/tasks/$taskId/requeue").toTask()
    }

    fun schedulerState(): TaskSchedulerCliState =
        request("GET", SCHEDULER_PATH).toSchedulerState()

    fun startScheduler(): TaskSchedulerCliState =
        request("POST", "$SCHEDULER_PATH/start").toSchedulerState()

    fun stopScheduler(): TaskSchedulerCliState =
        request("POST", "$SCHEDULER_PATH/stop").toSchedulerState()

    fun updateSchedulerWorkers(workerCount: Int): TaskSchedulerCliState {
        require(workerCount in 1..64) { "Worker count must be between 1 and 64." }
        return request(
            "PUT",
            "$SCHEDULER_PATH/workers",
            buildJsonObject { put("workerCount", workerCount) }
        ).toSchedulerState()
    }

    fun taskMetrics(): TaskMetricsCliSnapshot {
        val json = request("GET", "/api/v1/admin/task-metrics")
        return TaskMetricsCliSnapshot(
            queueDepth = json.long("queueDepth"),
            queuedCount = json.long("queuedCount"),
            retryingCount = json.long("retryingCount"),
            runningCount = json.long("runningCount"),
            succeededCount = json.long("succeededCount"),
            failedCount = json.long("failedCount"),
            timedOutCount = json.long("timedOutCount"),
            averageExecutionDurationMs = json.nullableDouble("averageExecutionDurationMs"),
            collectedAt = json.string("collectedAt")
        )
    }

    fun workerFleet(): DockerWorkerFleetCliState = request("GET", WORKER_PATH).toWorkerFleet()

    fun worker(workerId: String): DockerWorkerCliState {
        requireWorkerId(workerId)
        return workerFleet().members.firstOrNull { it.id == workerId }
            ?: throw IllegalArgumentException("Docker worker is not registered in the fleet: $workerId")
    }

    fun startWorkerFleet(): DockerWorkerFleetCliState =
        request("POST", "$WORKER_PATH/start").toWorkerFleet()

    fun stopWorkerFleet(): DockerWorkerFleetCliState =
        request("POST", "$WORKER_PATH/stop").toWorkerFleet()

    fun restartWorker(workerId: String): DockerWorkerCliState {
        requireWorkerId(workerId)
        return request("POST", "$WORKER_PATH/${encode(workerId)}/restart").toWorker()
    }

    fun workerLogs(workerId: String, tail: Int = 200, timeoutMs: Long = 5_000): DockerWorkerLogsCliResult {
        requireWorkerId(workerId)
        require(tail in 1..10_000) { "Log tail must be between 1 and 10000 lines." }
        require(timeoutMs > 0) { "Log timeout must be greater than zero." }
        val json = request("GET", "$WORKER_PATH/${encode(workerId)}/logs?tail=$tail&timeoutMs=$timeoutMs")
        return DockerWorkerLogsCliResult(
            workerId = json.string("workerId"),
            containerId = json.string("containerId"),
            content = json.string("content"),
            collectedAt = json.string("collectedAt")
        )
    }

    fun retentionOverview(): TaskRetentionCliOverview {
        val json = request("GET", RETENTION_PATH)
        return TaskRetentionCliOverview(
            policy = json.required("policy").jsonObject.toRetentionPolicy(),
            state = json.required("state").jsonObject.toRetentionState()
        )
    }

    fun retentionEstimate(): TaskRetentionCliEstimate {
        val json = request("GET", "$RETENTION_PATH/estimate")
        return TaskRetentionCliEstimate(
            cutoff = json.string("cutoff"),
            expiredTasks = json.long("expiredTasks"),
            collectedAt = json.string("collectedAt")
        )
    }

    fun runRetentionCleanup(): TaskCleanupCliResult =
        request("POST", "$RETENTION_PATH/cleanup").toCleanupResult()

    fun lastRetentionResult(): TaskRetentionCliState =
        request("GET", "$RETENTION_PATH/last-result").toRetentionState()

    internal fun requestElement(method: String, path: String, body: JsonObject? = null): JsonElement {
        val response = transport.execute(config, TaskHttpRequest(method, path, body?.toString()))
        if (response.status !in 200..299) {
            val message = runCatching {
                val error = Json.parseToJsonElement(response.body).jsonObject
                error["message"]?.jsonPrimitive?.contentOrNull
                    ?: error["error"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: response.body.take(500).ifBlank { "No response body" }
            throw TaskCliHttpException(response.status, message)
        }
        return runCatching { Json.parseToJsonElement(response.body) }
            .getOrElse { throw TaskCliHttpException(response.status, "Invalid JSON response: ${it.message}") }
    }

    internal fun decodeTask(json: JsonObject): TaskCliSummary = json.toTask()

    private fun request(method: String, path: String, body: JsonObject? = null): JsonObject =
        requestElement(method, path, body).jsonObject

    private fun SubmitTaskRequest.toJson() = buildJsonObject {
        put("projectId", projectId)
        put("pipelineId", pipelineId)
        putJsonObject("context") { context.forEach { (key, value) -> put(key, value) } }
        put("priority", priority)
        putJsonObject("retry") {
            put("maxAttempts", maxAttempts)
            put("initialDelayMs", initialDelayMs)
            put("backoffMultiplier", backoffMultiplier)
            put("maxDelayMs", maxDelayMs)
        }
        timeoutMs?.let { put("timeoutMs", it) }
    }

    private fun JsonObject.toTask() = TaskCliSummary(
        id = int("id"),
        projectId = int("projectId"),
        taskType = string("taskType"),
        status = string("status"),
        priority = int("priority"),
        attemptCount = int("attemptCount"),
        maxAttempts = int("maxAttempts"),
        nextAttemptAt = nullableString("nextAttemptAt"),
        timeoutMs = nullableLong("timeoutMs"),
        cancellationRequestedAt = nullableString("cancellationRequestedAt"),
        cancellationRequestedBy = nullableInt("cancellationRequestedBy"),
        progress = this["progress"]?.takeUnless { it is JsonNull }?.jsonObject?.let {
            TaskCliProgress(it.int("percent"), it.nullableString("message"), it.string("updatedAt"))
        },
        result = this["result"]?.takeUnless { it is JsonNull }?.jsonObject?.let {
            TaskCliResult(it.nullableString("outputPath"), it.nullableString("contentType"), it.stringMap("metadata"))
        },
        error = this["error"]?.takeUnless { it is JsonNull }?.jsonObject?.let {
            TaskCliError(
                it.string("code"), it.string("category"), it.string("message"),
                it.required("retryable").jsonPrimitive.content.toBooleanStrict(),
                it.stringMap("details"), it.string("occurredAt")
            )
        },
        createdAt = string("createdAt"),
        updatedAt = string("updatedAt"),
        startedAt = nullableString("startedAt"),
        completedAt = nullableString("completedAt")
    )

    private fun JsonObject.toSchedulerState() = TaskSchedulerCliState(
        status = string("status"),
        desiredWorkerCount = int("desiredWorkerCount"),
        activeWorkerCount = int("activeWorkerCount"),
        startedAt = nullableString("startedAt"),
        stoppedAt = nullableString("stoppedAt"),
        lastFailure = nullableString("lastFailure")
    )

    private fun JsonObject.toWorkerFleet() = DockerWorkerFleetCliState(
        healthy = required("healthy").jsonPrimitive.content.toBooleanStrict(),
        observedAt = string("observedAt"),
        members = required("members").jsonArray.map { it.jsonObject.toWorker() }
    )

    private fun JsonObject.toWorker() = DockerWorkerCliState(
        id = string("id"),
        status = string("status"),
        containerName = string("containerName"),
        endpoint = string("endpoint"),
        containerId = nullableString("containerId"),
        healthy = required("healthy").jsonPrimitive.content.toBooleanStrict(),
        detail = nullableString("detail"),
        observedAt = string("observedAt")
    )

    private fun requireWorkerId(workerId: String) {
        require(WORKER_ID.matches(workerId)) { "Invalid Docker worker ID: $workerId" }
    }

    private fun JsonObject.toRetentionPolicy() = TaskRetentionCliPolicy(
        enabled = required("enabled").jsonPrimitive.content.toBooleanStrict(),
        retentionDays = long("retentionDays"),
        cleanupIntervalMinutes = long("cleanupIntervalMinutes"),
        batchSize = int("batchSize"),
        maxBatchesPerRun = int("maxBatchesPerRun")
    )

    private fun JsonObject.toCleanupResult() = TaskCleanupCliResult(
        cutoff = string("cutoff"),
        deletedTasks = int("deletedTasks"),
        completedBatches = int("completedBatches"),
        completedAt = string("completedAt")
    )

    private fun JsonObject.toRetentionState() = TaskRetentionCliState(
        running = required("running").jsonPrimitive.content.toBooleanStrict(),
        lastStartedAt = nullableString("lastStartedAt"),
        lastResult = this["lastResult"]?.takeUnless { it is JsonNull }?.jsonObject?.toCleanupResult(),
        lastFailure = nullableString("lastFailure")
    )

    private fun JsonObject.required(name: String) = this[name] ?: error("Response is missing '$name'.")
    private fun JsonObject.string(name: String) = required(name).jsonPrimitive.content
    private fun JsonObject.int(name: String) = required(name).jsonPrimitive.int
    private fun JsonObject.long(name: String) = required(name).jsonPrimitive.long
    private fun JsonObject.nullableString(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.nullableInt(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int
    private fun JsonObject.nullableLong(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long
    private fun JsonObject.nullableDouble(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.double
    private fun JsonObject.stringMap(name: String) = required(name).jsonObject.mapValues { it.value.jsonPrimitive.content }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        private const val SCHEDULER_PATH = "/api/v1/admin/task-scheduler"
        private const val RETENTION_PATH = "/api/v1/admin/task-retention"
        private const val WORKER_PATH = "/api/v1/admin/docker-workers"
        private val WORKER_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
    }
}
