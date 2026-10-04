package me.moiclathy.edb.core.template.repository

import me.moiclathy.edb.core.template.model.TemplateSummary

data class TemplateSearchResult(

    val total: Long,

    val templates: List<TemplateSummary>

)