package me.moiclathy.edb.core.task.runtime

interface TaskRunner {
    suspend fun runOnce(): Boolean
}
