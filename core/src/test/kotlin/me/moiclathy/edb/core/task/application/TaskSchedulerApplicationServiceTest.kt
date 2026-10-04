package me.moiclathy.edb.core.task.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.task.scheduler.TaskScheduler
import me.moiclathy.edb.core.task.scheduler.TaskSchedulerState
import me.moiclathy.edb.core.task.scheduler.TaskSchedulerStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TaskSchedulerApplicationServiceTest {
    @Test
    fun `controls scheduler and exposes current concurrency`() = runBlocking {
        val scheduler = RecordingScheduler()
        val service = TaskSchedulerApplicationService(scheduler)

        assertEquals(TaskSchedulerStatus.STOPPED, service.state().status)
        assertEquals(TaskSchedulerStatus.RUNNING, service.start().status)
        val resized = service.updateWorkerCount(4)
        assertEquals(4, resized.desiredWorkerCount)
        assertEquals(4, resized.activeWorkerCount)
        assertEquals(TaskSchedulerStatus.STOPPED, service.stop().status)
    }

    private class RecordingScheduler : TaskScheduler {
        private val mutableState = MutableStateFlow(
            TaskSchedulerState(TaskSchedulerStatus.STOPPED, 1, 0)
        )
        override val state: StateFlow<TaskSchedulerState> = mutableState

        override fun start(): Boolean {
            mutableState.value = mutableState.value.copy(
                status = TaskSchedulerStatus.RUNNING,
                activeWorkerCount = mutableState.value.desiredWorkerCount
            )
            return true
        }

        override fun updateWorkerCount(workerCount: Int) {
            mutableState.value = mutableState.value.copy(
                desiredWorkerCount = workerCount,
                activeWorkerCount = workerCount
            )
        }

        override suspend fun stop(): Boolean {
            mutableState.value = mutableState.value.copy(
                status = TaskSchedulerStatus.STOPPED,
                activeWorkerCount = 0
            )
            return true
        }

        override fun close() = Unit
    }
}
