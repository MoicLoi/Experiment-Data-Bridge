package me.moiclathy.edb.core.datafile.lifecycle

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataFileOrphanCleanupAdminService(
    val policy: DataFileOrphanCleanupPolicy,
    private val service: DataFileOrphanCleanupService,
    private val manager: DataFileOrphanCleanupManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun state(): DataFileOrphanCleanupManagerState = manager.state.value

    suspend fun estimate(): DataFileOrphanCleanupEstimate =
        withContext(dispatcher) { service.estimate() }

    suspend fun cleanup(): DataFileOrphanCleanupResult = manager.runNow()
}
