package me.moiclathy.edb.cli.service

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path

data class DataFileCliSummary(
    val id: Int,
    val projectId: Int,
    val createdByUserId: Int,
    val kind: String,
    val format: String,
    val status: String,
    val originalFileName: String,
    val mediaType: String?,
    val sizeBytes: Long?,
    val sha256: String?,
    val storageProvider: String?,
    val origin: String,
    val parentDataFileIds: Set<Int>,
    val producingTaskId: Int?,
    val externalSystem: String?,
    val metadata: JsonObject,
    val createdAt: String,
    val updatedAt: String
)

data class TaskSubmissionCliOptions(
    val priority: Int = 50,
    val maxAttempts: Int = 1,
    val initialDelayMs: Long = 1_000,
    val backoffMultiplier: Double = 2.0,
    val maxDelayMs: Long = 60_000,
    val timeoutMs: Long? = null
)

data class DataFileDownloadResult(
    val path: Path,
    val bytesWritten: Long,
    val contentType: String?
)

data class DataFileConversionOptionCli(
    val name: String,
    val type: String,
    val required: Boolean,
    val defaultValue: String?,
    val description: String?
)

data class DataFileConversionCapabilityCli(
    val sourceFormat: String,
    val targetFormat: String,
    val options: List<DataFileConversionOptionCli>
)

data class AnalysisHandlerCli(
    val id: String,
    val analysisType: String,
    val tool: String,
    val outputFormat: String
)

data class ReportDefinitionCli(
    val id: String,
    val version: Int,
    val title: String,
    val generatorId: String,
    val outputFormat: String,
    val outputFileName: String,
    val defaultParameters: Map<String, String>
)

data class DataFileOrphanPolicyCli(
    val enabled: Boolean,
    val minimumAgeHours: Long,
    val cleanupIntervalMinutes: Long,
    val maxDeletesPerRun: Int
)

data class DataFileOrphanResultCli(
    val cutoff: String,
    val scannedFiles: Int,
    val orphanCandidates: Int,
    val deletedFiles: Int,
    val deletedBytes: Long,
    val failures: Int,
    val completedAt: String
)

data class DataFileOrphanStateCli(
    val running: Boolean,
    val lastStartedAt: String?,
    val lastResult: DataFileOrphanResultCli?,
    val lastFailure: String?
)

data class DataFileOrphanOverviewCli(
    val policy: DataFileOrphanPolicyCli,
    val state: DataFileOrphanStateCli
)

data class DataFileOrphanEstimateCli(
    val cutoff: String,
    val scannedFiles: Int,
    val orphanFiles: Int,
    val orphanBytes: Long,
    val collectedAt: String
)

data class DataFileRetentionPolicyCli(
    val enabled: Boolean,
    val retentionDays: Long,
    val cleanupIntervalMinutes: Long,
    val batchSize: Int,
    val maxBatchesPerRun: Int
)

data class DataFileRetentionResultCli(
    val cutoff: String,
    val deletedRecords: Int,
    val completedBatches: Int,
    val completedAt: String
)

data class DataFileRetentionStateCli(
    val running: Boolean,
    val lastStartedAt: String?,
    val lastResult: DataFileRetentionResultCli?,
    val lastFailure: String?
)

data class DataFileRetentionOverviewCli(
    val policy: DataFileRetentionPolicyCli,
    val state: DataFileRetentionStateCli
)

data class DataFileRetentionEstimateCli(
    val cutoff: String,
    val expiredDeletedFiles: Long,
    val collectedAt: String
)
