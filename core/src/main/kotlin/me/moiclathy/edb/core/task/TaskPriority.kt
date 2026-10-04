package me.moiclathy.edb.core.task

/**
 * Scheduling priority in the inclusive range 0..100.
 * Higher values are claimed first; equal priorities keep FIFO ordering.
 */
@JvmInline
value class TaskPriority(val value: Int) : Comparable<TaskPriority> {
    init {
        require(value in MIN_VALUE..MAX_VALUE) {
            "Task priority must be between $MIN_VALUE and $MAX_VALUE."
        }
    }

    override fun compareTo(other: TaskPriority): Int = value.compareTo(other.value)

    companion object {
        const val MIN_VALUE = 0
        const val MAX_VALUE = 100

        val LOW = TaskPriority(25)
        val NORMAL = TaskPriority(50)
        val HIGH = TaskPriority(75)
        val CRITICAL = TaskPriority(100)
    }
}
