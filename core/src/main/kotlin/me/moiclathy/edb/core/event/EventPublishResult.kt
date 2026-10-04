package me.moiclathy.edb.core.event

data class EventPublishResult(
    val matchedSubscribers: Int,
    val acceptedSubscribers: Int
)
