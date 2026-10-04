package me.moiclathy.edb.core.template.repository

import me.moiclathy.edb.core.database.DatabaseFactory
import me.moiclathy.edb.core.database.table.TemplateTable
import me.moiclathy.edb.core.template.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction

class MySqlTemplateRepository(
    private val databaseFactory: DatabaseFactory
) : TemplateRepository {

    override fun save(
        template: Template
    ): Template {
        val id = transaction {
            TemplateTable.insert {
                it[name] = template.name
                it[description] = template.description
                it[type] = template.type.name
                it[visibility] = template.visibility.name
                it[version] = template.metadata.version
                it[author] = template.metadata.author
                it[createdAt] = template.metadata.createdAt
                it[updatedAt] = template.metadata.updatedAt
                it[yaml] = template.yaml
            }[TemplateTable.id]
        }

        return template.copy(id = id)
    }

    override fun update(
        template: Template
    ): Template {
        transaction {
            TemplateTable.update(
                {
                    TemplateTable.id eq template.id!!
                }
            ) {
                it[name] = template.name
                it[description] = template.description
                it[type] = template.type.name
                it[visibility] = template.visibility.name
                it[version] = template.metadata.version
                it[author] = template.metadata.author
                it[updatedAt] = template.metadata.updatedAt
                it[yaml] = template.yaml
            }
        }

        return template
    }

    override fun findById(
        id: Long
    ): Template? {
        return transaction {
            TemplateTable
                .selectAll()
                .where {
                    TemplateTable.id eq id
                }
                .map {
                    rowToModel(it)
                }
                .singleOrNull()
        }
    }

    override fun findByName(
        name: String
    ): Template? {
        return transaction {
            TemplateTable
                .selectAll()
                .where {
                    TemplateTable.name eq name
                }
                .map {
                    rowToModel(it)
                }
                .singleOrNull()
        }
    }

    override fun search(
        query: TemplateQuery
    ): List<TemplateSummary> {
        return transaction {
            val queryBuilder = TemplateTable.selectAll()

            // 正确的 Exposed WHERE 动态过滤写法
            query.keyword?.let { keyword ->
                queryBuilder.andWhere {
                    TemplateTable.name.like("%$keyword%")
                }
            }

            queryBuilder.map {
                TemplateSummary(
                    id = it[TemplateTable.id],
                    name = it[TemplateTable.name],
                    type = TemplateType.valueOf(it[TemplateTable.type]),
                    visibility = TemplateVisibility.valueOf(it[TemplateTable.visibility])
                )
            }
        }
    }

    override fun delete(
        id: Long
    ) {
        transaction {
            TemplateTable.deleteWhere {
                TemplateTable.id eq id
            }
        }
    }

    override fun exists(
        name: String
    ): Boolean {
        return findByName(name) != null
    }

    override fun count(): Long {
        return transaction {
            TemplateTable
                .selectAll()
                .count()
        }
    }

    private fun rowToModel(
        row: ResultRow
    ): Template {
        return Template(
            id = row[TemplateTable.id],
            name = row[TemplateTable.name],
            description = row[TemplateTable.description],
            type = TemplateType.valueOf(row[TemplateTable.type]),
            visibility = TemplateVisibility.valueOf(row[TemplateTable.visibility]),
            metadata = TemplateMetadata(
                createdAt = row[TemplateTable.createdAt],
                updatedAt = row[TemplateTable.updatedAt],
                author = row[TemplateTable.author],
                version = row[TemplateTable.version]
            ),
            yaml = row[TemplateTable.yaml]
        )
    }
}