package me.moiclathy.edb.core.task.application

import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus

data class TaskListQuery(
    val projectId: Int,
    val status: TaskStatus? = null,
    val page: Int = 0,
    val size: Int = 20,
    val requestedBy: Int
) {
    init {
        require(projectId > 0) { "Project ID must be greater than zero." }
        require(page >= 0) { "Page must not be negative." }
        require(size in 1..MAX_PAGE_SIZE) { "Page size must be between 1 and $MAX_PAGE_SIZE." }
        require(requestedBy > 0) { "Requesting user ID must be greater than zero." }
    }

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

data class TaskPage(
    val items: List<Task>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Long
)
