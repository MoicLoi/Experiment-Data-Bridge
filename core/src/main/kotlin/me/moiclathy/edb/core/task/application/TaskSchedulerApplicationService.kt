package me.moiclathy.edb.core.task.application

import me.moiclathy.edb.core.task.scheduler.TaskScheduler
import me.moiclathy.edb.core.task.scheduler.TaskSchedulerState

/** Administrative application boundary for scheduler control and observation. */
class TaskSchedulerApplicationService(
    private val scheduler: TaskScheduler
) {
    fun state(): TaskSchedulerState = scheduler.state.value

    fun start(): TaskSchedulerState {
        scheduler.start()
        return state()
    }

    fun updateWorkerCount(workerCount: Int): TaskSchedulerState {
        require(workerCount in 1..MAX_WORKER_COUNT) {
            "Worker count must be between 1 and $MAX_WORKER_COUNT."
        }
        scheduler.updateWorkerCount(workerCount)
        return state()
    }

    suspend fun stop(): TaskSchedulerState {
        scheduler.stop()
        return state()
    }

    companion object {
        const val MAX_WORKER_COUNT = 64
    }
}
