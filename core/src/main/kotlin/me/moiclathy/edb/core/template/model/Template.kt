package me.moiclathy.edb.core.template.model

data class Template(

    val id: Long?,

    val name: String,

    val description: String,

    val type: TemplateType,

    val visibility: TemplateVisibility,

    val metadata: TemplateMetadata,

    val yaml: String

)