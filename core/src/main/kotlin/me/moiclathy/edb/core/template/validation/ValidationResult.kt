package me.moiclathy.edb.core.template.validation

data class TemplateValidationResult(

    val valid: Boolean,

    val errors: List<String>

)