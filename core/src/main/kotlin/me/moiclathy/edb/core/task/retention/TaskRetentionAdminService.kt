package me.moiclathy.edb.core.task.retention

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskRetentionAdminService(
    val policy: TaskRetentionPolicy,
    private val retentionService: TaskRetentionService,
    private val manager: TaskRetentionManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun state(): TaskRetentionManagerState = manager.state.value

    suspend fun estimate(): TaskRetentionEstimate =
        withContext(dispatcher) { retentionService.estimate() }

    suspend fun cleanup(): TaskCleanupResult = manager.runNow()
}
