package me.moiclathy.edb.web.bootstrap

import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.moiclathy.edb.core.task.scheduler.TaskScheduler
import me.moiclathy.edb.core.task.scheduler.TaskSchedulerState
import me.moiclathy.edb.core.task.scheduler.TaskSchedulerStatus
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class TaskSchedulerLifecycleTest {
    @Test
    fun `starts and stops scheduler with application lifecycle`() {
        val scheduler = RecordingTaskScheduler()

        testApplication {
            application {
                configureTaskSchedulerLifecycle(scheduler)
            }
            startApplication()
            assertEquals(1, scheduler.startCount.get())
            assertEquals(TaskSchedulerStatus.RUNNING, scheduler.state.value.status)
        }

        assertEquals(1, scheduler.stopCount.get())
        assertEquals(TaskSchedulerStatus.STOPPED, scheduler.state.value.status)
    }

    private class RecordingTaskScheduler : TaskScheduler {
        private val mutableState = MutableStateFlow(
            TaskSchedulerState(
                status = TaskSchedulerStatus.STOPPED,
                desiredWorkerCount = 2,
                activeWorkerCount = 0
            )
        )
        override val state: StateFlow<TaskSchedulerState> = mutableState
        val startCount = AtomicInteger()
        val stopCount = AtomicInteger()

        override fun start(): Boolean {
            startCount.incrementAndGet()
            mutableState.value = mutableState.value.copy(
                status = TaskSchedulerStatus.RUNNING,
                activeWorkerCount = 2
            )
            return true
        }

        override fun updateWorkerCount(workerCount: Int) = Unit

        override suspend fun stop(): Boolean {
            stopCount.incrementAndGet()
            mutableState.value = mutableState.value.copy(
                status = TaskSchedulerStatus.STOPPED,
                activeWorkerCount = 0
            )
            return true
        }

        override fun close() = Unit
    }
}
