package me.moiclathy.edb.agent.event

import me.moiclathy.edb.core.event.EventBus

fun interface AgentEventPublisher {
    suspend fun publish(event: AgentLifecycleEvent)

    data object None : AgentEventPublisher {
        override suspend fun publish(event: AgentLifecycleEvent) = Unit
    }
}

class EventBusAgentEventPublisher(private val eventBus: EventBus) : AgentEventPublisher {
    override suspend fun publish(event: AgentLifecycleEvent) {
        if (!eventBus.isClosed) eventBus.publish(event)
    }
}
