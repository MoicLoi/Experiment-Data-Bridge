package me.moiclathy.edb.core.event

fun interface EventSubscriberErrorHandler {
    fun handle(subscriptionId: Long, event: Event, error: Throwable)
}
