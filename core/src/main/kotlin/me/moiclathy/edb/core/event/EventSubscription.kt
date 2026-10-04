package me.moiclathy.edb.core.event

interface EventSubscription {
    val id: Long
    val isActive: Boolean

    fun cancel()
}
