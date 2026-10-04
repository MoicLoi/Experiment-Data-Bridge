package me.moiclathy.edb.core.task.cancellation

import java.time.Instant

data class TaskCancellationRequest(
    val requestedAt: Instant,
    val requestedBy: Int
) {
    init {
        require(requestedBy > 0) { "Cancellation requester ID must be greater than zero." }
    }
}
