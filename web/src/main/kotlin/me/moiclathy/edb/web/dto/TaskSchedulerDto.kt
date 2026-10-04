package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.task.scheduler.TaskSchedulerState

@Serializable
data class UpdateTaskSchedulerWorkersRequest(
    val workerCount: Int
)

@Serializable
data class TaskSchedulerStateResponse(
    val status: String,
    val desiredWorkerCount: Int,
    val activeWorkerCount: Int,
    val startedAt: String?,
    val stoppedAt: String?,
    val lastFailure: String?
) {
    companion object {
        fun from(state: TaskSchedulerState) = TaskSchedulerStateResponse(
            status = state.status.name,
            desiredWorkerCount = state.desiredWorkerCount,
            activeWorkerCount = state.activeWorkerCount,
            startedAt = state.startedAt?.toString(),
            stoppedAt = state.stoppedAt?.toString(),
            lastFailure = state.lastFailure
        )
    }
}
