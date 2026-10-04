package me.moiclathy.edb.core.template.model

data class TemplateQuery(

    val keyword: String? = null,

    val type: TemplateType? = null,

    val visibility: TemplateVisibility? = null

)