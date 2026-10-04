package me.moiclathy.edb.web.dto

data class ConfigValidationResponse(

    val code:String,

    val message:String,

    val errors:List<ValidationItem>

)

data class ValidationItem(

    val field:String,

    val message:String

)