package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Path

class DataFileApiClient(
    private val config: TaskServerConfig,
    jsonTransport: TaskHttpTransport = JdkTaskHttpTransport(),
    private val binaryTransport: DataFileBinaryTransport = JdkDataFileBinaryTransport()
) {
    private val api = TaskApiClient(config, jsonTransport)

    fun upload(projectId: Int, file: Path, mediaType: String? = null): DataFileCliSummary {
        positive(projectId, "Project")
        val response = binaryTransport.upload(config, projectId, file, mediaType)
        return parseResponse(response).jsonObject.toDataFile()
    }

    fun list(projectId: Int): List<DataFileCliSummary> {
        positive(projectId, "Project")
        return api.requestElement("GET", projectPath(projectId)).jsonArray.map { it.jsonObject.toDataFile() }
    }

    fun get(projectId: Int, dataFileId: Int): DataFileCliSummary {
        positive(projectId, "Project")
        positive(dataFileId, "DataFile")
        return api.requestElement("GET", "${projectPath(projectId)}/$dataFileId").jsonObject.toDataFile()
    }

    fun delete(projectId: Int, dataFileId: Int): DataFileCliSummary {
        positive(projectId, "Project")
        positive(dataFileId, "DataFile")
        return api.requestElement("DELETE", "${projectPath(projectId)}/$dataFileId").jsonObject.toDataFile()
    }

    fun download(
        projectId: Int,
        dataFileId: Int,
        target: Path,
        force: Boolean = false
    ): DataFileDownloadResult {
        positive(projectId, "Project")
        positive(dataFileId, "DataFile")
        return binaryTransport.download(config, projectId, dataFileId, target, force)
    }

    fun conversionTargets(projectId: Int, dataFileId: Int): List<DataFileConversionCapabilityCli> {
        positive(projectId, "Project")
        positive(dataFileId, "DataFile")
        return api.requestElement(
            "GET",
            "${projectPath(projectId)}/$dataFileId/conversion-targets"
        ).jsonArray.map { element ->
            val capability = element.jsonObject
            DataFileConversionCapabilityCli(
                sourceFormat = capability.string("sourceFormat"),
                targetFormat = capability.string("targetFormat"),
                options = capability.required("options").jsonArray.map { optionElement ->
                    val option = optionElement.jsonObject
                    DataFileConversionOptionCli(
                        name = option.string("name"),
                        type = option.string("type"),
                        required = option.boolean("required"),
                        defaultValue = option.nullableString("defaultValue"),
                        description = option.nullableString("description")
                    )
                }
            )
        }
    }

    fun conversionArtifacts(projectId: Int, dataFileId: Int): List<DataFileCliSummary> =
        artifacts(projectId, dataFileId, "conversions")

    fun analysisHandlers(projectId: Int): List<AnalysisHandlerCli> {
        positive(projectId, "Project")
        return api.requestElement("GET", "/api/v1/projects/$projectId/analysis-handlers")
            .jsonArray.map { element ->
                val handler = element.jsonObject
                AnalysisHandlerCli(
                    id = handler.string("id"),
                    analysisType = handler.string("analysisType"),
                    tool = handler.string("tool"),
                    outputFormat = handler.string("outputFormat")
                )
            }
    }

    fun analysisArtifacts(projectId: Int, dataFileId: Int): List<DataFileCliSummary> =
        artifacts(projectId, dataFileId, "analyses")

    fun reportDefinitions(projectId: Int): List<ReportDefinitionCli> {
        positive(projectId, "Project")
        return api.requestElement("GET", "/api/v1/projects/$projectId/reports/definitions")
            .jsonArray.map { element ->
                val definition = element.jsonObject
                ReportDefinitionCli(
                    id = definition.string("id"),
                    version = definition.int("version"),
                    title = definition.string("title"),
                    generatorId = definition.string("generatorId"),
                    outputFormat = definition.string("outputFormat"),
                    outputFileName = definition.string("outputFileName"),
                    defaultParameters = definition.stringMap("defaultParameters")
                )
            }
    }

    fun reportArtifacts(projectId: Int, dataFileId: Int): List<DataFileCliSummary> =
        artifacts(projectId, dataFileId, "reports")

    fun orphanOverview(): DataFileOrphanOverviewCli {
        val json = api.requestElement("GET", ORPHAN_PATH).jsonObject
        return DataFileOrphanOverviewCli(
            json.required("policy").jsonObject.toOrphanPolicy(),
            json.required("state").jsonObject.toOrphanState()
        )
    }

    fun orphanEstimate(): DataFileOrphanEstimateCli {
        val json = api.requestElement("GET", "$ORPHAN_PATH/estimate").jsonObject
        return DataFileOrphanEstimateCli(
            cutoff = json.string("cutoff"),
            scannedFiles = json.int("scannedFiles"),
            orphanFiles = json.int("orphanFiles"),
            orphanBytes = json.long("orphanBytes"),
            collectedAt = json.string("collectedAt")
        )
    }

    fun runOrphanCleanup(): DataFileOrphanResultCli =
        api.requestElement("POST", "$ORPHAN_PATH/cleanup").jsonObject.toOrphanResult()

    fun lastOrphanResult(): DataFileOrphanStateCli =
        api.requestElement("GET", "$ORPHAN_PATH/last-result").jsonObject.toOrphanState()

    fun retentionOverview(): DataFileRetentionOverviewCli {
        val json = api.requestElement("GET", RETENTION_PATH).jsonObject
        return DataFileRetentionOverviewCli(
            json.required("policy").jsonObject.toRetentionPolicy(),
            json.required("state").jsonObject.toRetentionState()
        )
    }

    fun retentionEstimate(): DataFileRetentionEstimateCli {
        val json = api.requestElement("GET", "$RETENTION_PATH/estimate").jsonObject
        return DataFileRetentionEstimateCli(
            cutoff = json.string("cutoff"),
            expiredDeletedFiles = json.long("expiredDeletedFiles"),
            collectedAt = json.string("collectedAt")
        )
    }

    fun runRetentionCleanup(): DataFileRetentionResultCli =
        api.requestElement("POST", "$RETENTION_PATH/cleanup").jsonObject.toRetentionResult()

    fun lastRetentionResult(): DataFileRetentionStateCli =
        api.requestElement("GET", "$RETENTION_PATH/last-result").jsonObject.toRetentionState()

    fun submitConversion(
        projectId: Int,
        dataFileId: Int,
        targetFormat: String,
        options: Map<String, String>,
        execution: TaskSubmissionCliOptions
    ): TaskCliSummary {
        positive(projectId, "Project")
        positive(dataFileId, "DataFile")
        require(targetFormat.isNotBlank()) { "Target format must not be blank." }
        val body = buildJsonObject {
            put("targetFormat", targetFormat.uppercase())
            putJsonObject("options") { options.forEach { (key, value) -> put(key, value) } }
            applyExecution(execution, this)
        }
        return task("POST", "${projectPath(projectId)}/$dataFileId/conversions", body)
    }

    fun submitAnalysis(
        projectId: Int,
        handlerId: String,
        sourceDataFileIds: Set<Int>,
        parameters: Map<String, String>,
        execution: TaskSubmissionCliOptions
    ): TaskCliSummary {
        positive(projectId, "Project")
        require(handlerId.isNotBlank()) { "Analysis handler ID must not be blank." }
        require(sourceDataFileIds.isNotEmpty()) { "At least one source DataFile ID is required." }
        sourceDataFileIds.forEach { positive(it, "DataFile") }
        val body = buildJsonObject {
            put("handlerId", handlerId)
            put("sourceDataFileIds", buildJsonArray { sourceDataFileIds.sorted().forEach { add(JsonPrimitive(it)) } })
            putJsonObject("parameters") { parameters.forEach { (key, value) -> put(key, value) } }
            applyExecution(execution, this)
        }
        return task("POST", "/api/v1/projects/$projectId/analyses", body)
    }

    fun submitReport(
        projectId: Int,
        definitionId: String,
        sourceDataFileIds: Set<Int>,
        parameters: Map<String, String>,
        execution: TaskSubmissionCliOptions
    ): TaskCliSummary {
        positive(projectId, "Project")
        require(definitionId.isNotBlank()) { "Report definition ID must not be blank." }
        sourceDataFileIds.forEach { positive(it, "DataFile") }
        val body = buildJsonObject {
            put("definitionId", definitionId)
            put("sourceDataFileIds", buildJsonArray { sourceDataFileIds.sorted().forEach { add(JsonPrimitive(it)) } })
            putJsonObject("parameters") { parameters.forEach { (key, value) -> put(key, value) } }
            applyExecution(execution, this)
        }
        return task("POST", "/api/v1/projects/$projectId/reports", body)
    }

    private fun task(method: String, path: String, body: JsonObject): TaskCliSummary =
        api.decodeTask(api.requestElement(method, path, body).jsonObject)

    private fun artifacts(projectId: Int, dataFileId: Int, resource: String): List<DataFileCliSummary> {
        positive(projectId, "Project")
        positive(dataFileId, "DataFile")
        return api.requestElement("GET", "${projectPath(projectId)}/$dataFileId/$resource")
            .jsonArray.map { it.jsonObject.toDataFile() }
    }

    private fun parseResponse(response: TaskHttpResponse): JsonElement {
        if (response.status !in 200..299) {
            val message = runCatching {
                val error = Json.parseToJsonElement(response.body).jsonObject
                error["message"]?.jsonPrimitive?.content ?: error["error"]?.jsonPrimitive?.content
            }.getOrNull() ?: response.body.take(500)
            throw TaskCliHttpException(response.status, message)
        }
        return runCatching { Json.parseToJsonElement(response.body) }
            .getOrElse { throw TaskCliHttpException(response.status, "Invalid JSON response: ${it.message}") }
    }

    private fun JsonObject.toDataFile() = DataFileCliSummary(
        id = int("id"),
        projectId = int("projectId"),
        createdByUserId = int("createdByUserId"),
        kind = string("kind"),
        format = string("format"),
        status = string("status"),
        originalFileName = string("originalFileName"),
        mediaType = nullableString("mediaType"),
        sizeBytes = nullableLong("sizeBytes"),
        sha256 = nullableString("sha256"),
        storageProvider = nullableString("storageProvider"),
        origin = string("origin"),
        parentDataFileIds = required("parentDataFileIds").jsonArray.mapTo(linkedSetOf()) { it.jsonPrimitive.int },
        producingTaskId = nullableInt("producingTaskId"),
        externalSystem = nullableString("externalSystem"),
        metadata = required("metadata").jsonObject,
        createdAt = string("createdAt"),
        updatedAt = string("updatedAt")
    )

    private fun JsonObject.toOrphanPolicy() = DataFileOrphanPolicyCli(
        enabled = boolean("enabled"),
        minimumAgeHours = long("minimumAgeHours"),
        cleanupIntervalMinutes = long("cleanupIntervalMinutes"),
        maxDeletesPerRun = int("maxDeletesPerRun")
    )

    private fun JsonObject.toOrphanResult() = DataFileOrphanResultCli(
        cutoff = string("cutoff"),
        scannedFiles = int("scannedFiles"),
        orphanCandidates = int("orphanCandidates"),
        deletedFiles = int("deletedFiles"),
        deletedBytes = long("deletedBytes"),
        failures = int("failures"),
        completedAt = string("completedAt")
    )

    private fun JsonObject.toOrphanState() = DataFileOrphanStateCli(
        running = boolean("running"),
        lastStartedAt = nullableString("lastStartedAt"),
        lastResult = this["lastResult"]?.takeUnless { it is JsonNull }?.jsonObject?.toOrphanResult(),
        lastFailure = nullableString("lastFailure")
    )

    private fun JsonObject.toRetentionPolicy() = DataFileRetentionPolicyCli(
        enabled = boolean("enabled"),
        retentionDays = long("retentionDays"),
        cleanupIntervalMinutes = long("cleanupIntervalMinutes"),
        batchSize = int("batchSize"),
        maxBatchesPerRun = int("maxBatchesPerRun")
    )

    private fun JsonObject.toRetentionResult() = DataFileRetentionResultCli(
        cutoff = string("cutoff"),
        deletedRecords = int("deletedRecords"),
        completedBatches = int("completedBatches"),
        completedAt = string("completedAt")
    )

    private fun JsonObject.toRetentionState() = DataFileRetentionStateCli(
        running = boolean("running"),
        lastStartedAt = nullableString("lastStartedAt"),
        lastResult = this["lastResult"]?.takeUnless { it is JsonNull }?.jsonObject?.toRetentionResult(),
        lastFailure = nullableString("lastFailure")
    )

    private fun applyExecution(
        execution: TaskSubmissionCliOptions,
        json: kotlinx.serialization.json.JsonObjectBuilder
    ) = with(execution) {
        require(priority in 0..100) { "Priority must be between 0 and 100." }
        require(maxAttempts > 0) { "Maximum attempts must be greater than zero." }
        require(initialDelayMs >= 0 && maxDelayMs >= 0) { "Retry delays cannot be negative." }
        require(backoffMultiplier >= 1.0) { "Backoff multiplier must be at least 1.0." }
        require(timeoutMs == null || timeoutMs > 0) { "Task timeout must be greater than zero." }
        json.put("priority", priority)
        json.putJsonObject("retry") {
            put("maxAttempts", maxAttempts)
            put("initialDelayMs", initialDelayMs)
            put("backoffMultiplier", backoffMultiplier)
            put("maxDelayMs", maxDelayMs)
        }
        timeoutMs?.let { json.put("timeoutMs", it) }
    }

    private fun projectPath(projectId: Int) = "/api/v1/projects/$projectId/data-files"
    private fun positive(value: Int, label: String) = require(value > 0) { "$label ID must be greater than zero." }
    private fun JsonObject.required(name: String) = this[name] ?: error("Response is missing '$name'.")
    private fun JsonObject.string(name: String) = required(name).jsonPrimitive.content
    private fun JsonObject.int(name: String) = required(name).jsonPrimitive.int
    private fun JsonObject.long(name: String) = required(name).jsonPrimitive.long
    private fun JsonObject.nullableString(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.nullableInt(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int
    private fun JsonObject.nullableLong(name: String) = this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long
    private fun JsonObject.boolean(name: String) = required(name).jsonPrimitive.content.toBooleanStrict()
    private fun JsonObject.stringMap(name: String) = required(name).jsonObject.mapValues { it.value.jsonPrimitive.content }

    companion object {
        private const val ORPHAN_PATH = "/api/v1/admin/data-file-orphan-cleanup"
        private const val RETENTION_PATH = "/api/v1/admin/data-file-retention"
    }
}
