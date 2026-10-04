package me.moiclathy.edb.web.dto

import me.moiclathy.edb.core.template.model.TemplateType
import me.moiclathy.edb.core.template.model.TemplateVisibility

data class UpdateTemplateRequest(

    val name: String,

    val description: String,

    val type: TemplateType,

    val visibility: TemplateVisibility,

    val yaml: String

)