package me.moiclathy.edb.web.dto

import me.moiclathy.edb.core.config.validation.ConfigurationValidationReport


data class ConfigurationValidationResponse(

    val valid: Boolean,

    val errors: List<ValidationErrorResponse>

)


data class ValidationErrorResponse(

    val field: String,

    val message: String

)


fun ConfigurationValidationReport.toResponse():

        ConfigurationValidationResponse {

    return ConfigurationValidationResponse(

        valid = valid,

        errors = errors.map {

            ValidationErrorResponse(

                field = it.field,

                message = it.message

            )

        }

    )

}