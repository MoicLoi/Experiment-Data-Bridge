package me.moiclathy.edb.core.database.table

import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.TaskPriority
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TasksTable : Table("tasks") {

    val id =
        integer("id")
            .autoIncrement()

    val projectId =
        reference(
            "project_id",
            ProjectsTable.id
        )

    val taskType =
        varchar("task_type", 50)

    val status =
        enumerationByName(
            "status",
            20,
            TaskStatus::class
        )

    val priority =
        integer("priority")
            .transform(
                wrap = ::TaskPriority,
                unwrap = TaskPriority::value
            )

    val attemptCount = integer("attempt_count")

    val maxAttempts = integer("max_attempts")

    val retryInitialDelayMs = long("retry_initial_delay_ms")

    val retryBackoffMultiplier = double("retry_backoff_multiplier")

    val retryMaxDelayMs = long("retry_max_delay_ms")

    val nextAttemptAt = timestamp("next_attempt_at").nullable()

    val timeoutMs = long("timeout_ms").nullable()

    val cancellationRequestedAt = timestamp("cancellation_requested_at").nullable()

    val cancellationRequestedBy = integer("cancellation_requested_by").nullable()

    val progressPercent = integer("progress_percent").nullable()

    val progressMessage = varchar("progress_message", 500).nullable()

    val progressUpdatedAt = timestamp("progress_updated_at").nullable()

    val configSnapshot =
        text("config_snapshot")
            .nullable()

    val idempotencyKey = varchar("idempotency_key", 191).nullable().uniqueIndex()

    val triggerUserId =
        reference(
            "trigger_user_id",
            UsersTable.id
        )

    val startedAt =
        timestamp("started_at")
            .nullable()

    val completedAt =
        timestamp("completed_at")
            .nullable()

    val resultPath =
        varchar("result_path", 500)
            .nullable()

    val resultContentType = varchar("result_content_type", 100).nullable()

    val resultMetadata = text("result_metadata").nullable()

    val errorMessage =
        text("error_message")
            .nullable()

    val errorCategory = varchar("error_category", 30).nullable()

    val errorCode = varchar("error_code", 100).nullable()

    val errorRetryable = bool("error_retryable").nullable()

    val errorDetails = text("error_details").nullable()

    val errorOccurredAt = timestamp("error_occurred_at").nullable()

    val createdAt =
        timestamp("created_at")

    val updatedAt =
        timestamp("updated_at")

    override val primaryKey =
        PrimaryKey(id)
}
