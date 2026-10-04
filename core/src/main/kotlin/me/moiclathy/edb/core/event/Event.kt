package me.moiclathy.edb.core.event

import java.time.Instant

interface Event {
    val occurredAt: Instant
}
