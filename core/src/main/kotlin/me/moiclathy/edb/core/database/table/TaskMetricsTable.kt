package me.moiclathy.edb.core.database.table

import org.jetbrains.exposed.sql.Table

object TaskMetricsTable : Table("task_metrics") {

    val id =
        integer("id")
            .autoIncrement()

    val taskId =
        reference(
            "task_id",
            TasksTable.id
        )

    val metricName =
        varchar("metric_name", 50)

    val metricValue =
        double("metric_value")
            .nullable()

    override val primaryKey =
        PrimaryKey(id)
}