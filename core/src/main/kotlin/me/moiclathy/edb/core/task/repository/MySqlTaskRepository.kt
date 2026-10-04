package me.moiclathy.edb.core.task.repository

import me.moiclathy.edb.core.database.table.TasksTable
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.persistence.TaskEntity
import me.moiclathy.edb.core.task.persistence.TaskMapper
import me.moiclathy.edb.core.task.retry.RetryPolicy
import me.moiclathy.edb.core.task.cancellation.TaskCancellationRequest
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.result.TaskResult
import me.moiclathy.edb.core.task.error.TaskError
import me.moiclathy.edb.core.task.error.TaskErrorCategory
import me.moiclathy.edb.core.task.persistence.TaskPayloadCodec
import me.moiclathy.edb.core.task.audit.TaskAuditEventFactory
import me.moiclathy.edb.core.task.audit.TaskAuditRepository
import me.moiclathy.edb.core.task.retry.TaskRequeueNotAllowedException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import java.time.Duration

class MySqlTaskRepository(
    private val auditRepository: TaskAuditRepository
) : TaskRepository {

    override fun save(task: Task): Task {
        require(task.id == null) { "A new task must not already have an ID." }
        val entity = TaskMapper.toEntity(task)

        return transaction {
            val id = TasksTable.insert { row ->
                row[projectId] = entity.projectId
                row[taskType] = entity.taskType
                row[status] = entity.status
                row[priority] = entity.priority
                row[attemptCount] = entity.attemptCount
                row[maxAttempts] = entity.retryPolicy.maxAttempts
                row[retryInitialDelayMs] = entity.retryPolicy.initialDelay.toMillis()
                row[retryBackoffMultiplier] = entity.retryPolicy.backoffMultiplier
                row[retryMaxDelayMs] = entity.retryPolicy.maxDelay.toMillis()
                row[nextAttemptAt] = entity.nextAttemptAt
                row[timeoutMs] = entity.timeout?.toMillis()
                row[cancellationRequestedAt] = entity.cancellationRequest?.requestedAt
                row[cancellationRequestedBy] = entity.cancellationRequest?.requestedBy
                row[progressPercent] = entity.progress?.percent
                row[progressMessage] = entity.progress?.message
                row[progressUpdatedAt] = entity.progress?.updatedAt
                row[configSnapshot] = entity.configSnapshot
                row[idempotencyKey] = entity.idempotencyKey
                row[triggerUserId] = entity.triggerUserId
                row[startedAt] = entity.startedAt
                row[completedAt] = entity.completedAt
                row[resultPath] = entity.result?.outputPath
                row[resultContentType] = entity.result?.contentType
                row[resultMetadata] = entity.result?.metadata?.let(TaskPayloadCodec::encode)
                row[errorMessage] = entity.error?.message
                row[errorCategory] = entity.error?.category?.name
                row[errorCode] = entity.error?.code
                row[errorRetryable] = entity.error?.retryable
                row[errorDetails] = entity.error?.details?.let(TaskPayloadCodec::encode)
                row[errorOccurredAt] = entity.error?.occurredAt
                row[createdAt] = entity.createdAt
                row[updatedAt] = entity.updatedAt
            }.resultedValues
                ?.single()
                ?.get(TasksTable.id)
                ?: error("Failed to create task")
            val persisted = task.copy(id = id)
            val events = buildList {
                add(TaskAuditEventFactory.created(persisted))
                if (persisted.status != TaskStatus.PENDING) {
                    TaskAuditEventFactory.transition(
                        before = persisted.copy(status = TaskStatus.PENDING),
                        after = persisted,
                        actorUserId = persisted.triggerUserId
                    )?.let(::add)
                }
            }
            auditRepository.appendAll(events)
            persisted
        }
    }

    override fun findById(id: Int): Task? {
        require(id > 0) { "Task ID must be greater than zero." }

        return transaction {
            TasksTable
                .selectAll()
                .where { TasksTable.id eq id }
                .limit(1)
                .singleOrNull()
                ?.toTask()
        }
    }

    override fun findByIdempotencyKey(key: String): Task? {
        require(key.isNotBlank()) { "Task idempotency key must not be blank." }
        return transaction {
            TasksTable.selectAll().where { TasksTable.idempotencyKey eq key }.limit(1).singleOrNull()?.toTask()
        }
    }

    override fun findByProjectId(projectId: Int): List<Task> {
        require(projectId > 0) { "Project ID must be greater than zero." }

        return transaction {
            TasksTable
                .selectAll()
                .where { TasksTable.projectId eq projectId }
                .orderBy(TasksTable.createdAt to SortOrder.DESC)
                .map { row -> row.toTask() }
        }
    }

    override fun findPage(
        projectId: Int,
        status: TaskStatus?,
        offset: Long,
        limit: Int
    ): List<Task> {
        require(projectId > 0) { "Project ID must be greater than zero." }
        require(offset >= 0) { "Offset must not be negative." }
        require(limit > 0) { "Limit must be greater than zero." }

        return transaction {
            TasksTable
                .selectAll()
                .where { queryCondition(projectId, status) }
                .orderBy(
                    TasksTable.createdAt to SortOrder.DESC,
                    TasksTable.id to SortOrder.DESC
                )
                .limit(limit)
                .offset(offset)
                .map { row -> row.toTask() }
        }
    }

    override fun count(projectId: Int, status: TaskStatus?): Long {
        require(projectId > 0) { "Project ID must be greater than zero." }
        return transaction {
            TasksTable
                .selectAll()
                .where { queryCondition(projectId, status) }
                .count()
        }
    }

    override fun findByStatus(status: TaskStatus, limit: Int): List<Task> {
        require(limit > 0) { "Limit must be greater than zero." }

        return transaction {
            TasksTable
                .selectAll()
                .where { TasksTable.status eq status }
                .orderBy(TasksTable.createdAt to SortOrder.ASC)
                .limit(limit)
                .map { row -> row.toTask() }
        }
    }

    override fun findQueued(limit: Int): List<Task> {
        require(limit > 0) { "Limit must be greater than zero." }

        return transaction {
            TasksTable
                .selectAll()
                .where { TasksTable.status eq TaskStatus.QUEUED }
                .orderBy(
                    TasksTable.priority to SortOrder.DESC,
                    TasksTable.createdAt to SortOrder.ASC,
                    TasksTable.id to SortOrder.ASC
                )
                .limit(limit)
                .map { row -> row.toTask() }
        }
    }

    override fun claimNextQueued(at: java.time.Instant): Task? = transaction {
        TasksTable.update(
            where = {
                (TasksTable.status eq TaskStatus.RETRYING) and
                    (TasksTable.nextAttemptAt lessEq at)
            }
        ) { row ->
            row[status] = TaskStatus.QUEUED
            row[nextAttemptAt] = null
            row[updatedAt] = at
        }

        val candidate = TasksTable
            .selectAll()
            .where { TasksTable.status eq TaskStatus.QUEUED }
            .orderBy(
                TasksTable.priority to SortOrder.DESC,
                TasksTable.createdAt to SortOrder.ASC,
                TasksTable.id to SortOrder.ASC
            )
            .limit(1)
            .forUpdate(
                ForUpdateOption.MySQL.ForUpdate(
                    ForUpdateOption.MySQL.MODE.SKIP_LOCKED
                )
            )
            .singleOrNull()
            ?.toTask()
            ?: return@transaction null

        // A task may be enqueued after the runtime captured its polling timestamp but
        // before this transaction locks the row. Keep persisted lifecycle timestamps
        // monotonic instead of terminating the worker coroutine for that valid race.
        val transitionAt = maxOf(at, candidate.updatedAt)
        val running = candidate
            .transitionTo(TaskStatus.RUNNING, transitionAt)
            .copy(
                attemptCount = candidate.attemptCount + 1,
                nextAttemptAt = null,
                error = null
            )
        val candidateId = requireNotNull(running.id)
        val updatedRows = TasksTable.update(
            where = {
                (TasksTable.id eq candidateId) and
                    (TasksTable.status eq TaskStatus.QUEUED)
            }
        ) { row ->
            row[status] = running.status
            row[attemptCount] = running.attemptCount
            row[startedAt] = running.startedAt
            row[completedAt] = running.completedAt
            row[nextAttemptAt] = running.nextAttemptAt
            row[errorMessage] = null
            row[errorCategory] = null
            row[errorCode] = null
            row[errorRetryable] = null
            row[errorDetails] = null
            row[errorOccurredAt] = null
            row[updatedAt] = running.updatedAt
        }

        check(updatedRows == 1) { "Failed to claim queued task: $candidateId" }
        TaskAuditEventFactory.transition(candidate, running)
            ?.let(auditRepository::append)
        running
    }

    override fun requestCancellation(
        taskId: Int,
        requestedBy: Int,
        at: java.time.Instant
    ): Task = transaction {
        require(taskId > 0) { "Task ID must be greater than zero." }
        require(requestedBy > 0) { "Cancellation requester ID must be greater than zero." }

        val current = TasksTable
            .selectAll()
            .where { TasksTable.id eq taskId }
            .limit(1)
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull()
            ?.toTask()
            ?: throw IllegalArgumentException("Task not found: $taskId")

        if (current.status.isTerminal) {
            return@transaction current
        }

        val request = TaskCancellationRequest(
            requestedAt = at,
            requestedBy = requestedBy
        )
        val cancelled = if (current.status == TaskStatus.RUNNING) {
            current.copy(
                cancellationRequest = request,
                updatedAt = at
            )
        } else {
            current
                .transitionTo(TaskStatus.CANCELLED, at)
                .copy(cancellationRequest = request)
        }

        TasksTable.update(
            where = { TasksTable.id eq taskId }
        ) { row ->
            row[status] = cancelled.status
            row[completedAt] = cancelled.completedAt
            row[cancellationRequestedAt] = request.requestedAt
            row[cancellationRequestedBy] = request.requestedBy
            row[updatedAt] = cancelled.updatedAt
        }

        val events = buildList {
            add(
                TaskAuditEventFactory.cancellationRequested(
                    task = current,
                    requestedBy = requestedBy,
                    requestedAt = at
                )
            )
            TaskAuditEventFactory.transition(
                before = current,
                after = cancelled,
                actorUserId = requestedBy
            )?.let(::add)
        }
        auditRepository.appendAll(events)

        cancelled
    }

    override fun requeueFailed(
        taskId: Int,
        requestedBy: Int,
        at: java.time.Instant
    ): Task = transaction {
        require(taskId > 0) { "Task ID must be greater than zero." }
        require(requestedBy > 0) { "Requeue requester ID must be greater than zero." }

        val current = TasksTable
            .selectAll()
            .where { TasksTable.id eq taskId }
            .limit(1)
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull()
            ?.toTask()
            ?: throw IllegalArgumentException("Task not found: $taskId")
        if (current.status !in setOf(TaskStatus.FAILED, TaskStatus.TIMED_OUT)) {
            throw TaskRequeueNotAllowedException(taskId, current.status)
        }

        val requeued = current
            .transitionTo(TaskStatus.QUEUED, at)
            .copy(
                attemptCount = 0,
                nextAttemptAt = null,
                cancellationRequest = null,
                progress = null,
                startedAt = null,
                completedAt = null,
                result = null,
                error = null
            )
        TasksTable.update(where = { TasksTable.id eq taskId }) { row ->
            row[status] = requeued.status
            row[attemptCount] = requeued.attemptCount
            row[nextAttemptAt] = null
            row[cancellationRequestedAt] = null
            row[cancellationRequestedBy] = null
            row[progressPercent] = null
            row[progressMessage] = null
            row[progressUpdatedAt] = null
            row[startedAt] = null
            row[completedAt] = null
            row[resultPath] = null
            row[resultContentType] = null
            row[resultMetadata] = null
            row[errorMessage] = null
            row[errorCategory] = null
            row[errorCode] = null
            row[errorRetryable] = null
            row[errorDetails] = null
            row[errorOccurredAt] = null
            row[updatedAt] = at
        }
        auditRepository.append(
            TaskAuditEventFactory.manuallyRequeued(current, requeued, requestedBy)
        )
        requeued
    }

    override fun updateProgress(taskId: Int, progress: TaskProgress): Task = transaction {
        require(taskId > 0) { "Task ID must be greater than zero." }

        val updatedRows = TasksTable.update(
            where = {
                (TasksTable.id eq taskId) and
                    (TasksTable.status eq TaskStatus.RUNNING)
            }
        ) { row ->
            row[progressPercent] = progress.percent
            row[progressMessage] = progress.message
            row[progressUpdatedAt] = progress.updatedAt
            row[updatedAt] = progress.updatedAt
        }

        require(updatedRows == 1) { "Running task not found: $taskId" }
        TasksTable
            .selectAll()
            .where { TasksTable.id eq taskId }
            .limit(1)
            .single()
            .toTask()
    }

    override fun update(task: Task): Task {
        val id = requireNotNull(task.id) { "A persisted task must have an ID." }
        val entity = TaskMapper.toEntity(task)

        val updatedRows = transaction {
            val before = TasksTable
                .selectAll()
                .where { TasksTable.id eq id }
                .limit(1)
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull()
                ?.toTask()
                ?: throw IllegalArgumentException("Task not found: $id")

            TasksTable.update(
                where = { TasksTable.id eq id }
            ) { row ->
                row[projectId] = entity.projectId
                row[taskType] = entity.taskType
                row[status] = entity.status
                row[priority] = entity.priority
                row[attemptCount] = entity.attemptCount
                row[maxAttempts] = entity.retryPolicy.maxAttempts
                row[retryInitialDelayMs] = entity.retryPolicy.initialDelay.toMillis()
                row[retryBackoffMultiplier] = entity.retryPolicy.backoffMultiplier
                row[retryMaxDelayMs] = entity.retryPolicy.maxDelay.toMillis()
                row[nextAttemptAt] = entity.nextAttemptAt
                row[timeoutMs] = entity.timeout?.toMillis()
                row[cancellationRequestedAt] = entity.cancellationRequest?.requestedAt
                row[cancellationRequestedBy] = entity.cancellationRequest?.requestedBy
                row[progressPercent] = entity.progress?.percent
                row[progressMessage] = entity.progress?.message
                row[progressUpdatedAt] = entity.progress?.updatedAt
                row[configSnapshot] = entity.configSnapshot
                row[idempotencyKey] = entity.idempotencyKey
                row[triggerUserId] = entity.triggerUserId
                row[startedAt] = entity.startedAt
                row[completedAt] = entity.completedAt
                row[resultPath] = entity.result?.outputPath
                row[resultContentType] = entity.result?.contentType
                row[resultMetadata] = entity.result?.metadata?.let(TaskPayloadCodec::encode)
                row[errorMessage] = entity.error?.message
                row[errorCategory] = entity.error?.category?.name
                row[errorCode] = entity.error?.code
                row[errorRetryable] = entity.error?.retryable
                row[errorDetails] = entity.error?.details?.let(TaskPayloadCodec::encode)
                row[errorOccurredAt] = entity.error?.occurredAt
                row[createdAt] = entity.createdAt
                row[updatedAt] = entity.updatedAt
            }
                .also {
                    TaskAuditEventFactory.transition(before, task)
                        ?.let(auditRepository::append)
                }
        }

        require(updatedRows == 1) { "Task not found: $id" }
        return task
    }

    override fun delete(id: Int) {
        require(id > 0) { "Task ID must be greater than zero." }

        transaction {
            TasksTable.deleteWhere { TasksTable.id eq id }
        }
    }

    private fun queryCondition(projectId: Int, status: TaskStatus?): Op<Boolean> {
        val projectCondition = TasksTable.projectId eq projectId
        return status?.let { projectCondition and (TasksTable.status eq it) }
            ?: projectCondition
    }

    private fun ResultRow.toTask(): Task = TaskMapper.toModel(
        TaskEntity(
            id = this[TasksTable.id],
            projectId = this[TasksTable.projectId],
            taskType = this[TasksTable.taskType],
            status = this[TasksTable.status],
            priority = this[TasksTable.priority],
            retryPolicy = RetryPolicy(
                maxAttempts = this[TasksTable.maxAttempts],
                initialDelay = Duration.ofMillis(this[TasksTable.retryInitialDelayMs]),
                backoffMultiplier = this[TasksTable.retryBackoffMultiplier],
                maxDelay = Duration.ofMillis(this[TasksTable.retryMaxDelayMs])
            ),
            attemptCount = this[TasksTable.attemptCount],
            nextAttemptAt = this[TasksTable.nextAttemptAt],
            timeout = this[TasksTable.timeoutMs]?.let(Duration::ofMillis),
            cancellationRequest = this[TasksTable.cancellationRequestedAt]?.let { requestedAt ->
                TaskCancellationRequest(
                    requestedAt = requestedAt,
                    requestedBy = requireNotNull(this[TasksTable.cancellationRequestedBy]) {
                        "Task cancellation requester is missing"
                    }
                )
            },
            progress = this[TasksTable.progressPercent]?.let { percent ->
                TaskProgress(
                    percent = percent,
                    message = this[TasksTable.progressMessage],
                    updatedAt = requireNotNull(this[TasksTable.progressUpdatedAt]) {
                        "Task progress timestamp is missing"
                    }
                )
            },
            configSnapshot = this[TasksTable.configSnapshot],
            idempotencyKey = this[TasksTable.idempotencyKey],
            triggerUserId = this[TasksTable.triggerUserId],
            startedAt = this[TasksTable.startedAt],
            completedAt = this[TasksTable.completedAt],
            result = this[TasksTable.resultMetadata]?.let { metadata ->
                TaskResult(
                    outputPath = this[TasksTable.resultPath],
                    contentType = this[TasksTable.resultContentType],
                    metadata = TaskPayloadCodec.decode(metadata)
                )
            } ?: this[TasksTable.resultPath]?.let { legacyPath ->
                TaskResult(outputPath = legacyPath)
            },
            error = this[TasksTable.errorMessage]?.let { message ->
                TaskError(
                    code = this[TasksTable.errorCode] ?: "LEGACY_ERROR",
                    category = this[TasksTable.errorCategory]
                        ?.let(TaskErrorCategory::valueOf)
                        ?: TaskErrorCategory.EXECUTION,
                    message = message,
                    retryable = this[TasksTable.errorRetryable] ?: false,
                    details = TaskPayloadCodec.decode(this[TasksTable.errorDetails]),
                    occurredAt = this[TasksTable.errorOccurredAt]
                        ?: this[TasksTable.completedAt]
                        ?: this[TasksTable.updatedAt]
                )
            },
            createdAt = this[TasksTable.createdAt],
            updatedAt = this[TasksTable.updatedAt]
        )
    )
}
