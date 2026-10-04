package me.moiclathy.edb.core.config.snapshot.repository

import me.moiclathy.edb.core.config.snapshot.model.ConfigurationSnapshot

interface ConfigurationSnapshotRepository {

    fun save(
        snapshot: ConfigurationSnapshot
    ): ConfigurationSnapshot

    fun findById(
        id: Long
    ): ConfigurationSnapshot?

    fun findLatest(
        projectId: Int
    ): ConfigurationSnapshot?

    fun findByProject(
        projectId: Int
    ): List<ConfigurationSnapshot>

    fun delete(
        id: Long
    )

    fun count(
        projectId: Int
    ): Long
}