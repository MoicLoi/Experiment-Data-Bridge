package me.moiclathy.edb.core.task.error

import java.time.Instant

interface TaskErrorClassifier {
    fun classify(error: Exception, occurredAt: Instant): TaskError
}
