package me.moiclathy.edb.core.task.repository

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.progress.TaskProgress

interface TaskRepository {

    fun save(task: Task): Task

    fun findById(id: Int): Task?

    fun findByIdempotencyKey(key: String): Task?

    fun findByProjectId(projectId: Int): List<Task>

    fun findPage(
        projectId: Int,
        status: TaskStatus?,
        offset: Long,
        limit: Int
    ): List<Task>

    fun count(projectId: Int, status: TaskStatus?): Long

    fun findByStatus(status: TaskStatus, limit: Int = 100): List<Task>

    fun findQueued(limit: Int = 100): List<Task>

    /** Atomically locks and transitions the next queued task to RUNNING. */
    fun claimNextQueued(at: java.time.Instant): Task?

    fun requestCancellation(taskId: Int, requestedBy: Int, at: java.time.Instant): Task

    fun requeueFailed(taskId: Int, requestedBy: Int, at: java.time.Instant): Task

    fun updateProgress(taskId: Int, progress: TaskProgress): Task

    fun update(task: Task): Task

    fun delete(id: Int)
}
