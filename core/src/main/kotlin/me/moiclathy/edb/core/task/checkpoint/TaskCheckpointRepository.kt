package me.moiclathy.edb.core.task.checkpoint

interface TaskCheckpointRepository {
    fun findByTaskId(taskId: Int): TaskCheckpoint?
    fun save(checkpoint: TaskCheckpoint): TaskCheckpoint
    fun deleteByTaskId(taskId: Int)
}
