package me.moiclathy.edb.core.config.snapshot.model

import java.time.Instant

data class SnapshotMetadata(

    val createdAt: Instant,

    val createdBy: String,

    val description: String? = null
)