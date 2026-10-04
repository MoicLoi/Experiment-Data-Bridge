package me.moiclathy.edb.core.event

enum class EventBufferStrategy {
    SUSPEND,
    DROP_OLDEST,
    DROP_LATEST
}
