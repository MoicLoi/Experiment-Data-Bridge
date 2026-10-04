package me.moiclathy.edb.core.event

import kotlin.reflect.KClass

interface EventBus : AutoCloseable {
    suspend fun publish(event: Event): EventPublishResult

    fun <E : Event> subscribe(
        eventType: KClass<E>,
        handler: suspend (E) -> Unit
    ): EventSubscription

    val isClosed: Boolean
}

inline fun <reified E : Event> EventBus.subscribe(
    noinline handler: suspend (E) -> Unit
): EventSubscription = subscribe(E::class, handler)
