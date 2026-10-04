package me.moiclathy.edb.core.task.audit

import me.moiclathy.edb.core.database.table.TaskAuditEventsTable
import me.moiclathy.edb.core.task.persistence.TaskPayloadCodec
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class MySqlTaskAuditRepository : TaskAuditRepository {

    override fun append(event: TaskAuditEvent): TaskAuditEvent =
        appendAll(listOf(event)).single()

    override fun appendAll(events: List<TaskAuditEvent>): List<TaskAuditEvent> {
        if (events.isEmpty()) return emptyList()

        return transaction {
            events.map { event ->
                require(event.id == null) { "A new task audit event must not have an ID." }
                val id = TaskAuditEventsTable.insert { row ->
                    row[taskId] = event.taskId
                    row[eventType] = event.type
                    row[fromStatus] = event.fromStatus
                    row[toStatus] = event.toStatus
                    row[actorUserId] = event.actorUserId
                    row[details] = event.details
                        .takeIf { it.isNotEmpty() }
                        ?.let(TaskPayloadCodec::encode)
                    row[createdAt] = event.createdAt
                }.resultedValues
                    ?.single()
                    ?.get(TaskAuditEventsTable.id)
                    ?.value
                    ?: error("Failed to create task audit event")
                event.copy(id = id)
            }
        }
    }

    override fun findByTaskId(taskId: Int): List<TaskAuditEvent> {
        require(taskId > 0) { "Task ID must be greater than zero." }

        return transaction {
            TaskAuditEventsTable
                .selectAll()
                .where { TaskAuditEventsTable.taskId eq taskId }
                .orderBy(
                    TaskAuditEventsTable.createdAt to SortOrder.ASC,
                    TaskAuditEventsTable.id to SortOrder.ASC
                )
                .map { row -> row.toEvent() }
        }
    }

    private fun ResultRow.toEvent(): TaskAuditEvent = TaskAuditEvent(
        id = this[TaskAuditEventsTable.id].value,
        taskId = this[TaskAuditEventsTable.taskId],
        type = this[TaskAuditEventsTable.eventType],
        fromStatus = this[TaskAuditEventsTable.fromStatus],
        toStatus = this[TaskAuditEventsTable.toStatus],
        actorUserId = this[TaskAuditEventsTable.actorUserId],
        details = TaskPayloadCodec.decode(this[TaskAuditEventsTable.details]),
        createdAt = this[TaskAuditEventsTable.createdAt]
    )
}
