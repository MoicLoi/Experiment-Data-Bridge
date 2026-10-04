package me.moiclathy.edb.core.config.metadata.repository

import me.moiclathy.edb.core.config.metadata.model.ConfigurationMetadata
import me.moiclathy.edb.core.config.metadata.persistence.ConfigurationMetadataEntity
import me.moiclathy.edb.core.config.metadata.persistence.ConfigurationMetadataMapper
import me.moiclathy.edb.core.database.table.ConfigurationMetadataTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * MySQL Configuration Metadata Repository。
 *
 * 当前设计：
 *
 * projectId 为主键。
 * 一个 project 对应一份 ConfigurationMetadata。
 *
 * tags 在数据库中保存为 JSON 字符串。
 */
class MySqlConfigurationMetadataRepository : ConfigurationMetadataRepository {

    override fun save(
        metadata: ConfigurationMetadata
    ): ConfigurationMetadata {

        val entity =
            ConfigurationMetadataMapper.toEntity(metadata)

        transaction {

            ConfigurationMetadataTable.insert { row ->

                row[ConfigurationMetadataTable.projectId] =
                    entity.projectId

                row[ConfigurationMetadataTable.description] =
                    entity.description

                row[ConfigurationMetadataTable.category] =
                    entity.category

                row[ConfigurationMetadataTable.tags] =
                    entity.tags

                row[ConfigurationMetadataTable.updatedAt] =
                    entity.updatedAt

                row[ConfigurationMetadataTable.dataSource] =
                    entity.dataSource
            }
        }

        return findByProjectId(
            metadata.projectId
        ) ?: throw IllegalStateException(
            "Failed to load saved configuration metadata for project ${metadata.projectId}"
        )
    }

    override fun findByProjectId(
        projectId: Int
    ): ConfigurationMetadata? {

        if (projectId <= 0) {
            return null
        }

        return transaction {

            ConfigurationMetadataTable
                .selectAll()
                .where {
                    ConfigurationMetadataTable.projectId eq projectId
                }
                .limit(1)
                .map {
                    ConfigurationMetadataMapper.toModel(
                        ConfigurationMetadataEntity(
                            projectId =
                                it[ConfigurationMetadataTable.projectId],

                            description =
                                it[ConfigurationMetadataTable.description],

                            category =
                                it[ConfigurationMetadataTable.category],

                            tags =
                                it[ConfigurationMetadataTable.tags],

                            updatedAt =
                                it[ConfigurationMetadataTable.updatedAt],

                            dataSource =
                                it[ConfigurationMetadataTable.dataSource]
                        )
                    )
                }
                .singleOrNull()
        }
    }

    override fun update(
        metadata: ConfigurationMetadata
    ): ConfigurationMetadata {

        require(metadata.projectId > 0) {
            "Project ID must be greater than zero."
        }

        val entity =
            ConfigurationMetadataMapper.toEntity(metadata)

        val updatedRows =
            transaction {

                ConfigurationMetadataTable.update(
                    where = {
                        ConfigurationMetadataTable.projectId eq
                                entity.projectId
                    }
                ) { row ->

                    row[ConfigurationMetadataTable.description] =
                        entity.description

                    row[ConfigurationMetadataTable.category] =
                        entity.category

                    row[ConfigurationMetadataTable.tags] =
                        entity.tags

                    row[ConfigurationMetadataTable.updatedAt] =
                        entity.updatedAt

                    row[ConfigurationMetadataTable.dataSource] =
                        entity.dataSource
                }
            }

        if (updatedRows == 0) {
            throw IllegalArgumentException(
                "Configuration metadata not found for project ${metadata.projectId}"
            )
        }

        return findByProjectId(
            metadata.projectId
        ) ?: throw IllegalStateException(
            "Failed to load updated configuration metadata for project ${metadata.projectId}"
        )
    }

    override fun delete(
        projectId: Int
    ) {

        if (projectId <= 0) {
            return
        }

        transaction {

            ConfigurationMetadataTable.deleteWhere {
                ConfigurationMetadataTable.projectId eq projectId
            }
        }
    }

    override fun exists(
        projectId: Int
    ): Boolean {

        if (projectId <= 0) {
            return false
        }

        return transaction {

            ConfigurationMetadataTable
                .selectAll()
                .where {
                    ConfigurationMetadataTable.projectId eq projectId
                }
                .limit(1)
                .any()
        }
    }

    override fun findAll(): List<ConfigurationMetadata> {

        return transaction {

            ConfigurationMetadataTable
                .selectAll()
                .map {

                    ConfigurationMetadataMapper.toModel(
                        ConfigurationMetadataEntity(
                            projectId =
                                it[ConfigurationMetadataTable.projectId],

                            description =
                                it[ConfigurationMetadataTable.description],

                            category =
                                it[ConfigurationMetadataTable.category],

                            tags =
                                it[ConfigurationMetadataTable.tags],

                            updatedAt =
                                it[ConfigurationMetadataTable.updatedAt],

                            dataSource =
                                it[ConfigurationMetadataTable.dataSource]
                        )
                    )
                }
        }
    }
}