package me.moiclathy.edb.core.task.runtime

import kotlinx.coroutines.CancellationException
import me.moiclathy.edb.core.event.EventBus
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.event.TaskLifecycleEvent
import me.moiclathy.edb.core.task.event.TaskLifecycleEventType
import org.apache.logging.log4j.LogManager
import java.time.Instant
import java.time.Duration

class TaskLifecycleEventPublisher(
    private val eventBus: EventBus
) {
    suspend fun publishCreated(task: Task) {
        publish(
            task = task,
            type = TaskLifecycleEventType.CREATED,
            previousStatus = null,
            status = TaskStatus.PENDING,
            occurredAt = task.createdAt
        )
    }

    suspend fun publishTransition(beforeStatus: TaskStatus, task: Task) {
        val type = when (task.status) {
            TaskStatus.QUEUED -> TaskLifecycleEventType.ENQUEUED
            TaskStatus.RUNNING -> TaskLifecycleEventType.CLAIMED
            TaskStatus.RETRYING -> TaskLifecycleEventType.RETRY_SCHEDULED
            TaskStatus.COMPLETED -> TaskLifecycleEventType.COMPLETED
            TaskStatus.FAILED -> TaskLifecycleEventType.FAILED
            TaskStatus.TIMED_OUT -> TaskLifecycleEventType.TIMED_OUT
            TaskStatus.CANCELLED -> TaskLifecycleEventType.CANCELLED
            TaskStatus.PENDING -> return
        }
        publish(task, type, beforeStatus, task.status, task.updatedAt)
    }

    suspend fun publishCancellationRequested(task: Task, previousStatus: TaskStatus) {
        val request = requireNotNull(task.cancellationRequest)
        publish(
            task = task,
            type = TaskLifecycleEventType.CANCELLATION_REQUESTED,
            previousStatus = previousStatus,
            status = previousStatus,
            occurredAt = request.requestedAt
        )
    }

    suspend fun publishProgress(task: Task) {
        publish(
            task = task,
            type = TaskLifecycleEventType.PROGRESS_UPDATED,
            previousStatus = task.status,
            status = task.status,
            occurredAt = requireNotNull(task.progress).updatedAt
        )
    }

    private suspend fun publish(
        task: Task,
        type: TaskLifecycleEventType,
        previousStatus: TaskStatus?,
        status: TaskStatus,
        occurredAt: Instant
    ) {
        val taskId = requireNotNull(task.id)
        val event = TaskLifecycleEvent(
            taskId = taskId,
            projectId = task.projectId,
            type = type,
            previousStatus = previousStatus,
            status = status,
            attemptCount = task.attemptCount,
            details = buildMap {
                put("taskType", task.taskType)
                task.nextAttemptAt?.let { put("nextAttemptAt", it.toString()) }
                task.result?.outputPath?.let { put("outputPath", it) }
                task.result?.metadata?.forEach { (key, value) ->
                    if (key in EVENT_RESULT_KEYS) put(key, value)
                }
                task.error?.let {
                    put("errorCode", it.code)
                    put("errorCategory", it.category.name)
                }
                task.cancellationRequest?.requestedBy?.let {
                    put("requestedBy", it.toString())
                }
                task.progress?.let {
                    put("progressPercent", it.percent.toString())
                    it.message?.let { message -> put("progressMessage", message) }
                }
                if (task.status.isTerminal) task.startedAt?.let { startedAt ->
                    put("durationMs", Duration.between(startedAt, task.completedAt ?: task.updatedAt).toMillis().coerceAtLeast(0).toString())
                }
            },
            occurredAt = occurredAt
        )

        try {
            eventBus.publish(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logger.error(
                "Failed to publish task lifecycle event {} for task {}",
                type,
                taskId,
                error
            )
        }
    }

    companion object {
        private val logger = LogManager.getLogger(TaskLifecycleEventPublisher::class.java)
        private val EVENT_RESULT_KEYS = setOf("dataFileId", "provenanceReportDataFileId", "analysisHandlerId", "format")
    }
}
