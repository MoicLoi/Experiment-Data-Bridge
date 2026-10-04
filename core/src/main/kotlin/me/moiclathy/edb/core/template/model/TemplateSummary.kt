package me.moiclathy.edb.core.template.model

data class TemplateSummary(

    val id: Long,

    val name: String,

    val type: TemplateType,

    val visibility: TemplateVisibility

)