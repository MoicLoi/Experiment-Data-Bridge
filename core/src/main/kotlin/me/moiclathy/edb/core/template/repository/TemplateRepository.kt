package me.moiclathy.edb.core.template.repository

import me.moiclathy.edb.core.template.model.Template
import me.moiclathy.edb.core.template.model.TemplateQuery
import me.moiclathy.edb.core.template.model.TemplateSummary

interface TemplateRepository {

    fun save(
        template: Template
    ): Template

    fun update(
        template: Template
    ): Template

    fun findById(
        id: Long
    ): Template?

    fun findByName(
        name: String
    ): Template?

    fun search(
        query: TemplateQuery
    ): List<TemplateSummary>

    fun delete(
        id: Long
    )

    fun exists(
        name: String
    ): Boolean

    fun count(): Long

}