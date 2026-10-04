package me.moiclathy.edb.core.config.snapshot.persistence

import java.time.Instant

data class ConfigurationSnapshotEntity(

    val id: Long?,

    val projectId: Int,

    val version: Int,

    val createdAt: Instant,

    val createdBy: String,

    val description: String?,

    val yaml: String
)