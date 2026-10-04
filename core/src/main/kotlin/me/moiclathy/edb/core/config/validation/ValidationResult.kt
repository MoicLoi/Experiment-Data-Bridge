package me.moiclathy.edb.core.config.validation

data class ValidationResult(

    val valid: Boolean,

    val errors: List<String>

)