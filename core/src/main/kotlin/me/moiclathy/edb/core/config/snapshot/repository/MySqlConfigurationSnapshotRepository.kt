package me.moiclathy.edb.core.config.snapshot.repository

import me.moiclathy.edb.core.config.snapshot.model.ConfigurationSnapshot
import me.moiclathy.edb.core.config.snapshot.persistence.ConfigurationSnapshotEntity
import me.moiclathy.edb.core.config.snapshot.persistence.ConfigurationSnapshotMapper
import me.moiclathy.edb.core.database.DatabaseFactory
import me.moiclathy.edb.core.database.table.ConfigurationSnapshotTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class MySqlConfigurationSnapshotRepository : ConfigurationSnapshotRepository {

    override fun save(
        snapshot: ConfigurationSnapshot
    ): ConfigurationSnapshot {

        val entity =
            ConfigurationSnapshotMapper.toEntity(snapshot)

        val id =
            transaction {

                ConfigurationSnapshotTable
                    .insert {
                        it[projectId] = entity.projectId
                        it[version] = entity.version
                        it[createdAt] = entity.createdAt
                        it[createdBy] = entity.createdBy
                        it[description] = entity.description
                        it[yaml] = entity.yaml
                    }
                    .resultedValues
                    ?.single()
                    ?.get(ConfigurationSnapshotTable.id)
                    ?.value
                    ?: error("Failed to create configuration snapshot")
            }

        return snapshot.copy(
            id = id
        )
    }

    override fun findById(
        id: Long
    ): ConfigurationSnapshot? {

        return transaction {

            ConfigurationSnapshotTable
                .selectAll()
                .where {
                    ConfigurationSnapshotTable.id eq id
                }
                .singleOrNull()
                ?.let {
                    ConfigurationSnapshotMapper.toModel(
                        ConfigurationSnapshotEntity(
                            id = it[ConfigurationSnapshotTable.id].value,
                            projectId = it[ConfigurationSnapshotTable.projectId],
                            version = it[ConfigurationSnapshotTable.version],
                            createdAt = it[ConfigurationSnapshotTable.createdAt],
                            createdBy = it[ConfigurationSnapshotTable.createdBy],
                            description = it[ConfigurationSnapshotTable.description],
                            yaml = it[ConfigurationSnapshotTable.yaml]
                        )
                    )
                }
        }
    }

    override fun findLatest(
        projectId: Int
    ): ConfigurationSnapshot? {

        return transaction {

            ConfigurationSnapshotTable
                .selectAll()
                .where {
                    ConfigurationSnapshotTable.projectId eq projectId
                }
                .orderBy(
                    ConfigurationSnapshotTable.version to SortOrder.DESC
                )
                .limit(1)
                .singleOrNull()
                ?.let {
                    ConfigurationSnapshotMapper.toModel(
                        ConfigurationSnapshotEntity(
                            id = it[ConfigurationSnapshotTable.id].value,
                            projectId = it[ConfigurationSnapshotTable.projectId],
                            version = it[ConfigurationSnapshotTable.version],
                            createdAt = it[ConfigurationSnapshotTable.createdAt],
                            createdBy = it[ConfigurationSnapshotTable.createdBy],
                            description = it[ConfigurationSnapshotTable.description],
                            yaml = it[ConfigurationSnapshotTable.yaml]
                        )
                    )
                }
        }
    }

    override fun findByProject(
        projectId: Int
    ): List<ConfigurationSnapshot> {

        return transaction {

            ConfigurationSnapshotTable
                .selectAll()
                .where {
                    ConfigurationSnapshotTable.projectId eq projectId
                }
                .orderBy(
                    ConfigurationSnapshotTable.version to SortOrder.DESC
                )
                .map {

                    ConfigurationSnapshotMapper.toModel(
                        ConfigurationSnapshotEntity(
                            id = it[ConfigurationSnapshotTable.id].value,
                            projectId = it[ConfigurationSnapshotTable.projectId],
                            version = it[ConfigurationSnapshotTable.version],
                            createdAt = it[ConfigurationSnapshotTable.createdAt],
                            createdBy = it[ConfigurationSnapshotTable.createdBy],
                            description = it[ConfigurationSnapshotTable.description],
                            yaml = it[ConfigurationSnapshotTable.yaml]
                        )
                    )
                }
        }
    }

    override fun delete(
        id: Long
    ) {

        transaction {

            ConfigurationSnapshotTable.deleteWhere {
                ConfigurationSnapshotTable.id eq id
            }
        }
    }

    override fun count(
        projectId: Int
    ): Long {

        return transaction {

            ConfigurationSnapshotTable
                .selectAll()
                .where {
                    ConfigurationSnapshotTable.projectId eq projectId
                }
                .count()
        }
    }
}