package me.moiclathy.edb.core.task.worker

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.task.runtime.TaskRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TaskWorkerTest {

    @Test
    fun `run once executes a claimed task and reports an empty queue`() = runBlocking {
        val runner = mockk<TaskRunner>()
        coEvery { runner.runOnce() } returnsMany listOf(true, false)
        val worker = TaskWorker(runner)

        assertTrue(worker.runOnce())
        assertFalse(worker.runOnce())
        coVerify(exactly = 2) { runner.runOnce() }
    }
}
